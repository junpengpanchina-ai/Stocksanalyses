#!/bin/bash

# KLine Analytics Deployment Script
set -e

# Configuration
NAMESPACE=${NAMESPACE:-kline-analytics}
ENVIRONMENT=${ENVIRONMENT:-staging}
CHART_PATH="./helm/kline-analytics"
VALUES_FILE="values-${ENVIRONMENT}.yaml"

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
    
    # Check if helm is installed
    if ! command -v helm &> /dev/null; then
        log_error "Helm is not installed. Please install Helm first."
        exit 1
    fi
    
    # Check if kubectl is installed
    if ! command -v kubectl &> /dev/null; then
        log_error "kubectl is not installed. Please install kubectl first."
        exit 1
    fi
    
    # Check if kubectl can connect to cluster
    if ! kubectl cluster-info &> /dev/null; then
        log_error "Cannot connect to Kubernetes cluster. Please check your kubeconfig."
        exit 1
    fi
    
    log_info "Prerequisites check passed"
}

create_namespace() {
    log_info "Creating namespace ${NAMESPACE}..."
    kubectl create namespace ${NAMESPACE} --dry-run=client -o yaml | kubectl apply -f -
}

install_dependencies() {
    log_info "Installing Helm dependencies..."
    helm dependency update ${CHART_PATH}
}

deploy_application() {
    log_info "Deploying KLine Analytics to ${ENVIRONMENT} environment..."
    
    # Check if values file exists
    if [ ! -f "${CHART_PATH}/${VALUES_FILE}" ]; then
        log_error "Values file ${VALUES_FILE} not found"
        exit 1
    fi
    
    # Deploy with Helm
    helm upgrade --install kline-analytics ${CHART_PATH} \
        --namespace ${NAMESPACE} \
        --values ${CHART_PATH}/${VALUES_FILE} \
        --wait \
        --timeout=10m \
        --create-namespace
    
    log_info "Deployment completed successfully"
}

verify_deployment() {
    log_info "Verifying deployment..."
    
    # Check if pods are running
    kubectl wait --for=condition=ready pod -l app.kubernetes.io/name=kline-analytics -n ${NAMESPACE} --timeout=300s
    
    # Check service
    kubectl get service kline-analytics -n ${NAMESPACE}
    
    # Check ingress if enabled
    if kubectl get ingress kline-analytics -n ${NAMESPACE} &> /dev/null; then
        kubectl get ingress kline-analytics -n ${NAMESPACE}
    fi
    
    log_info "Deployment verification completed"
}

show_status() {
    log_info "Deployment Status:"
    echo "=================="
    
    echo "Pods:"
    kubectl get pods -n ${NAMESPACE} -l app.kubernetes.io/name=kline-analytics
    
    echo ""
    echo "Services:"
    kubectl get services -n ${NAMESPACE} -l app.kubernetes.io/name=kline-analytics
    
    echo ""
    echo "Ingress:"
    kubectl get ingress -n ${NAMESPACE} -l app.kubernetes.io/name=kline-analytics 2>/dev/null || echo "No ingress found"
    
    echo ""
    echo "ConfigMaps:"
    kubectl get configmaps -n ${NAMESPACE} -l app.kubernetes.io/name=kline-analytics
    
    echo ""
    echo "Secrets:"
    kubectl get secrets -n ${NAMESPACE} -l app.kubernetes.io/name=kline-analytics
}

cleanup() {
    log_info "Cleaning up..."
    helm uninstall kline-analytics -n ${NAMESPACE} || true
    kubectl delete namespace ${NAMESPACE} || true
    log_info "Cleanup completed"
}

# Main script
case "${1:-deploy}" in
    "deploy")
        check_prerequisites
        create_namespace
        install_dependencies
        deploy_application
        verify_deployment
        show_status
        ;;
    "status")
        show_status
        ;;
    "cleanup")
        cleanup
        ;;
    "help")
        echo "Usage: $0 [deploy|status|cleanup|help]"
        echo ""
        echo "Commands:"
        echo "  deploy   - Deploy the application (default)"
        echo "  status   - Show deployment status"
        echo "  cleanup  - Remove the application"
        echo "  help     - Show this help message"
        echo ""
        echo "Environment variables:"
        echo "  NAMESPACE    - Kubernetes namespace (default: kline-analytics)"
        echo "  ENVIRONMENT  - Environment (development|staging|production|canary)"
        ;;
    *)
        log_error "Unknown command: $1"
        echo "Use '$0 help' for usage information"
        exit 1
        ;;
esac
