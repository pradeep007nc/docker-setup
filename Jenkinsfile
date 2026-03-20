pipeline {
    agent any

    environment {
        // The project name used by docker-compose to group containers
        COMPOSE_PROJECT_NAME = "dockerbackend"
    }

    stages {
        stage('Checkout') {
            steps {
                // Pulls the latest code from the branch configured in the Jenkins Job
                checkout scm
            }
        }

        stage('Build') {
            steps {
                script {
                    echo "Building the Docker image..."
                    // This builds the backend service defined in docker-compose.yml
                    sh "docker compose build backend"
                }
            }
        }

        stage('Deploy') {
            steps {
                script {
                    echo "Deploying the application..."
                    // Recreates the containers with the newly built image
                    // Note: Ensure your Jenkins credentials/environment variables are set
                    sh "docker compose up -d --force-recreate"
                }
            }
        }

        stage('Verify') {
            steps {
                script {
                    echo "Verifying deployment..."
                    sh "docker compose ps"
                }
            }
        }
    }

    post {
        always {
            echo "Cleaning up dangling images..."
            sh "docker image prune -f"
        }
        success {
            echo "Deployment of main branch successful."
        }
        failure {
            echo "Deployment failed. Please check the console output."
        }
    }
}
