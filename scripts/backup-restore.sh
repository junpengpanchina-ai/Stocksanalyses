#!/bin/bash

# KLine Analytics Backup and Restore Script
set -e

# Configuration
NAMESPACE=${NAMESPACE:-kline-analytics}
BACKUP_S3_BUCKET=${BACKUP_S3_BUCKET:-kline-analytics-backups}
AWS_REGION=${AWS_REGION:-us-west-2}
RETENTION_DAYS=${RETENTION_DAYS:-30}
CONCURRENCY=${CONCURRENCY:-3}

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Functions
log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

check_prerequisites() {
    log_info "Checking prerequisites..."
    
    # Check if kubectl is installed
    if ! command -v kubectl &> /dev/null; then
        log_error "kubectl is not installed. Please install kubectl first."
        exit 1
    fi
    
    # Check if aws cli is installed
    if ! command -v aws &> /dev/null; then
        log_error "AWS CLI is not installed. Please install AWS CLI first."
        exit 1
    fi
    
    # Check if kubectl can connect to cluster
    if ! kubectl cluster-info &> /dev/null; then
        log_error "Cannot connect to Kubernetes cluster. Please check your kubeconfig."
        exit 1
    fi
    
    # Check AWS credentials
    if ! aws sts get-caller-identity &> /dev/null; then
        log_error "AWS credentials not configured. Please run 'aws configure' first."
        exit 1
    fi
    
    log_info "Prerequisites check passed"
}

create_backup() {
    log_info "Creating backup..."
    
    TIMESTAMP=$(date +%Y%m%d_%H%M%S)
    BACKUP_FILE="kline_analytics_backup_${TIMESTAMP}.sql"
    COMPRESSED_FILE="${BACKUP_FILE}.gz"
    
    # Create backup job
    kubectl create job --from=cronjob/kline-analytics-backup kline-analytics-backup-manual -n ${NAMESPACE} || true
    
    # Wait for job completion
    kubectl wait --for=condition=complete job/kline-analytics-backup-manual -n ${NAMESPACE} --timeout=1800s
    
    # Get job logs
    kubectl logs job/kline-analytics-backup-manual -n ${NAMESPACE}
    
    # Cleanup job
    kubectl delete job kline-analytics-backup-manual -n ${NAMESPACE}
    
    log_info "Backup completed successfully"
}

list_backups() {
    log_info "Listing available backups..."
    
    aws s3 ls s3://${BACKUP_S3_BUCKET}/backups/ --recursive | \
        grep "kline_analytics_backup_" | \
        sort -r | \
        head -20
}

restore_backup() {
    local backup_file=$1
    
    if [ -z "$backup_file" ]; then
        log_error "Backup file not specified"
        echo "Usage: $0 restore <backup_file>"
        echo "Available backups:"
        list_backups
        exit 1
    fi
    
    log_info "Restoring backup: ${backup_file}"
    
    # Create restore job
    cat <<EOF | kubectl apply -f -
apiVersion: batch/v1
kind: Job
metadata:
  name: kline-analytics-restore-$(date +%s)
  namespace: ${NAMESPACE}
spec:
  template:
    spec:
      restartPolicy: Never
      serviceAccountName: kline-analytics-backup
      containers:
      - name: restore
        image: postgres:13-alpine
        command:
        - /bin/sh
        - -c
        - |
          set -e
          
          # Configuration
          RESTORE_DIR="/restore"
          BACKUP_FILE="${backup_file}"
          COMPRESSED_FILE="${BACKUP_FILE}.gz"
          CHECKSUM_FILE="${COMPRESSED_FILE}.sha256"
          
          # Create restore directory
          mkdir -p \${RESTORE_DIR}
          
          # Download backup from S3
          echo "Downloading backup from S3..."
          aws s3 cp s3://${BACKUP_S3_BUCKET}/backups/\${COMPRESSED_FILE} \${RESTORE_DIR}/\${COMPRESSED_FILE}
          aws s3 cp s3://${BACKUP_S3_BUCKET}/backups/\${CHECKSUM_FILE} \${RESTORE_DIR}/\${CHECKSUM_FILE}
          
          # Verify checksum
          echo "Verifying checksum..."
          cd \${RESTORE_DIR}
          sha256sum -c \${CHECKSUM_FILE}
          
          # Decompress backup
          echo "Decompressing backup..."
          gunzip \${COMPRESSED_FILE}
          
          # Create database if not exists
          echo "Creating database if not exists..."
          psql \\
            --host=\${DB_HOST} \\
            --port=\${DB_PORT} \\
            --username=\${DB_USER} \\
            --dbname=postgres \\
            --command="CREATE DATABASE \${DB_NAME} IF NOT EXISTS;"
          
          # Restore database
          echo "Restoring database..."
          psql \\
            --host=\${DB_HOST} \\
            --port=\${DB_PORT} \\
            --username=\${DB_USER} \\
            --dbname=\${DB_NAME} \\
            --file=\${RESTORE_DIR}/\${BACKUP_FILE}
          
          # Verify restore
          echo "Verifying restore..."
          psql \\
            --host=\${DB_HOST} \\
            --port=\${DB_PORT} \\
            --username=\${DB_USER} \\
            --dbname=\${DB_NAME} \\
            --command="SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public';"
          
          echo "Restore completed successfully"
        env:
        - name: DB_HOST
          valueFrom:
            secretKeyRef:
              name: kline-analytics-db-secret
              key: host
        - name: DB_PORT
          valueFrom:
            secretKeyRef:
              name: kline-analytics-db-secret
              key: port
        - name: DB_USER
          valueFrom:
            secretKeyRef:
              name: kline-analytics-db-secret
              key: username
        - name: DB_NAME
          valueFrom:
            secretKeyRef:
              name: kline-analytics-db-secret
              key: database
        - name: PGPASSWORD
          valueFrom:
            secretKeyRef:
              name: kline-analytics-db-secret
              key: password
        - name: S3_BUCKET
          value: "${BACKUP_S3_BUCKET}"
        - name: AWS_DEFAULT_REGION
          value: "${AWS_REGION}"
        - name: AWS_ACCESS_KEY_ID
          valueFrom:
            secretKeyRef:
              name: kline-analytics-s3-secret
              key: access-key-id
        - name: AWS_SECRET_ACCESS_KEY
          valueFrom:
            secretKeyRef:
              name: kline-analytics-s3-secret
              key: secret-access-key
        resources:
          limits:
            cpu: 1000m
            memory: 1Gi
          requests:
            cpu: 500m
            memory: 512Mi
        volumeMounts:
        - name: restore-storage
          mountPath: /restore
      volumes:
      - name: restore-storage
        emptyDir: {}
EOF
    
    # Wait for restore completion
    kubectl wait --for=condition=complete job/kline-analytics-restore-$(date +%s) -n ${NAMESPACE} --timeout=1800s
    
    # Get job logs
    kubectl logs job/kline-analytics-restore-$(date +%s) -n ${NAMESPACE}
    
    # Cleanup job
    kubectl delete job kline-analytics-restore-$(date +%s) -n ${NAMESPACE}
    
    log_info "Restore completed successfully"
}

cleanup_old_backups() {
    log_info "Cleaning up old backups (older than ${RETENTION_DAYS} days)..."
    
    # Calculate cutoff date
    CUTOFF_DATE=$(date -d "${RETENTION_DAYS} days ago" +%Y%m%d)
    
    # List and delete old backups
    aws s3 ls s3://${BACKUP_S3_BUCKET}/backups/ --recursive | \
        grep "kline_analytics_backup_" | \
        awk -v cutoff="${CUTOFF_DATE}" '$1 < cutoff {print $4}' | \
        xargs -I {} aws s3 rm s3://${BACKUP_S3_BUCKET}/{}
    
    log_info "Cleanup completed"
}

validate_backup() {
    local backup_file=$1
    
    if [ -z "$backup_file" ]; then
        log_error "Backup file not specified"
        echo "Usage: $0 validate <backup_file>"
        exit 1
    fi
    
    log_info "Validating backup: ${backup_file}"
    
    # Download and verify checksum
    aws s3 cp s3://${BACKUP_S3_BUCKET}/backups/${backup_file}.sha256 /tmp/
    aws s3 cp s3://${BACKUP_S3_BUCKET}/backups/${backup_file} /tmp/
    
    cd /tmp
    if sha256sum -c ${backup_file}.sha256; then
        log_info "Backup validation successful"
    else
        log_error "Backup validation failed"
        exit 1
    fi
    
    # Cleanup
    rm -f /tmp/${backup_file} /tmp/${backup_file}.sha256
}

# Main script
case "${1:-help}" in
    "backup")
        check_prerequisites
        create_backup
        ;;
    "restore")
        check_prerequisites
        restore_backup "$2"
        ;;
    "list")
        list_backups
        ;;
    "validate")
        check_prerequisites
        validate_backup "$2"
        ;;
    "cleanup")
        check_prerequisites
        cleanup_old_backups
        ;;
    "help")
        echo "Usage: $0 [backup|restore|list|validate|cleanup|help]"
        echo ""
        echo "Commands:"
        echo "  backup              - Create a new backup"
        echo "  restore <file>      - Restore from backup file"
        echo "  list                - List available backups"
        echo "  validate <file>     - Validate backup file"
        echo "  cleanup             - Clean up old backups"
        echo "  help                - Show this help message"
        echo ""
        echo "Environment variables:"
        echo "  NAMESPACE           - Kubernetes namespace (default: kline-analytics)"
        echo "  BACKUP_S3_BUCKET    - S3 bucket for backups (default: kline-analytics-backups)"
        echo "  AWS_REGION          - AWS region (default: us-west-2)"
        echo "  RETENTION_DAYS      - Backup retention days (default: 30)"
        echo "  CONCURRENCY         - Backup concurrency (default: 3)"
        ;;
    *)
        log_error "Unknown command: $1"
        echo "Use '$0 help' for usage information"
        exit 1
        ;;
esac
