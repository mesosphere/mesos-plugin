#!/usr/bin/env groovy

@Library('sec_ci_libs@v2-latest') _

def master_branches = ["master", "usi-jenkins", ] as String[]

ansiColor('xterm') {
  // using shakedown node because it's a lightweight alpine docker image instead of full VM
  node('shakedown') {
    stage("Verify author") {
      user_is_authorized(master_branches, '8b793652-f26a-422f-a9ba-0d1e47eb9d89', '#orchestration-dailies')
    }
  }
  node('jenkins-mesos-plugin-test') {
    stage('Build') {
      try {
        checkout scm
        sh 'sudo -E ./gradlew check --info'
      } finally {
        junit(allowEmptyResults: true, testResults: 'build/test-results/test/*.xml')
      }
    } 
  }
}

