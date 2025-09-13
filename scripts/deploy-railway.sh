#!/bin/bash

# Railway Deployment Script for KLine Analytics
# Usage: ./scripts/deploy-railway.sh

set -e

echo "🚀 Starting Railway deployment for KLine Analytics..."

# Check if Railway CLI is installed
if ! command -v railway &> /dev/null; then
    echo "❌ Railway CLI not found. Please install it first:"
    echo "   npm install -g @railway/cli"
    echo "   or visit: https://docs.railway.app/develop/cli"
    exit 1
fi

# Check if user is logged in
if ! railway whoami &> /dev/null; then
    echo "🔐 Please login to Railway first:"
    echo "   railway login"
    exit 1
fi

# Build the project
echo "🔨 Building Maven project..."
mvn clean package -DskipTests

# Check if JAR was created
if [ ! -f "target/kline-analytics-0.0.1-SNAPSHOT.jar" ]; then
    echo "❌ JAR file not found. Build failed."
    exit 1
fi

echo "✅ Build successful!"

# Deploy to Railway
echo "🚀 Deploying to Railway..."
railway up

echo "✅ Deployment initiated! Check Railway dashboard for progress."
echo "📊 Monitor logs with: railway logs"
echo "🌐 Your app will be available at the URL shown in Railway dashboard"
