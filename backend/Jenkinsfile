pipeline {
  agent any
  stages {
    stage('Spring Pull & Build') {
      steps {
        dir('/root/projects/spring-bitground') {
          sh 'git pull origin main'
          sh './gradlew clean bootJar'
          sh 'docker compose -f ~/deploy/docker-compose.yml build spring-app'
          sh 'docker compose -f ~/deploy/docker-compose.yml up -d spring-app'
        }
      }
    }
  }
}