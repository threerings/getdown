#!groovy
@Library('lvm-pipeline-library') _

pipeline {
  agent any

  tools {
    maven 'M3'
    jdk 'openjdk8'
  }

  options {
    // General Jenkins job properties
    buildDiscarder(logRotator(numToKeepStr: '5'))
    // "wrapper" steps that should wrap the entire build execution
    timestamps()
    // Skip default checkout to clean the workspace before the checkout is done manually
    skipDefaultCheckout()
  }

  triggers {
    // Once a day at random time
    cron BRANCH_NAME == "master" ? "@daily" : ""
  }

  parameters {
    booleanParam(defaultValue: false, description: 'Führt ein Maven-Release aus', name: 'release')
  }

  environment {
    MAVEN_OPTS="-Dmaven.repo.local=./m2"
  }

  stages {
    stage('Checkout') {
      steps {
        checkoutGit()
      }
    }

    stage('Build') {
      steps {
        script{
          // Fügt der Jenkins-Buildnummer die GIT-Commit-ID hinzu
          env.git_commit_id = sh returnStdout: true, script: 'git rev-parse HEAD'
          env.git_commit_id = env.git_commit_id.trim()
          env.git_commit_id_short = env.git_commit_id.take(7)
          currentBuild.displayName = "#${currentBuild.number}-${env.git_commit_id_short}"
          currentBuild.description = "Deploy:"
        }
        sh 'mvn clean compile -DskipTests=true -B'
      }
    }

    stage('Test') {
      steps {
        sh 'mvn verify -B'
      }
      post {
        always {
          junit allowEmptyResults: true, testResults: '**/target/*-reports/TEST-*.xml'
        }
      }
    }

    stage('Publish Artifacts (Nexus:Snapshots)') {
      when {
        branch 'master'
      }
      steps {
        sh 'mvn deploy -Dmaven.test.skip=true -DskipTests=true -B'
      }
    }

    stage('Release') {
      when {
        expression {
          return params.release
        }
      }
      steps {
        script {
          performMavenRelease()
        }
      }
      post {
        success {
          script {
            currentBuild.description = "Release: ${env.releaseVersion} | Deploy: "

            emailext(
                subject: "Version ${env.releaseVersion} of ${env.JOB_NAME} was released",
                body: """<p>Version ${env.releaseVersion} of ${env.JOB_NAME} was released</p>""",
                mimeType: 'text/html',
                recipientProviders: [[$class: 'DevelopersRecipientProvider'], [$class: 'RequesterRecipientProvider']]

            )
          }
        }
      }
    }
  }
  post {
    failure {
      emailext(
          subject: "FAILED: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]'",
          body: """<p>FAILED: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]':</p>
<p>Check console output at <a href='${env.BUILD_URL}'>${env.JOB_NAME} [${env.BUILD_NUMBER}]</a></p>""",
          mimeType: 'text/html',
          recipientProviders: [[$class: 'DevelopersRecipientProvider'], [$class: 'RequesterRecipientProvider']]
      )
    }
    always {
      generateDiscoBuildinfo()
    }
  }
}