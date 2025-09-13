pipeline {
  agent any
  options { timestamps() }
  stages {
    stage('UI Tests') {
      steps {
        sh '''
          set -e
          rm -rf artifacts || true
          mkdir -p artifacts
          docker run --rm \
            -e ARTIFACTS_DIR=/tmp/artifacts \
            -e DOWNLOADS_DIR=/tmp/downloads \
            -v "$PWD/artifacts":/tmp/artifacts \
            frankw0042/clj-playwright:1.54.0 \
            clojure -M -m webtest.core owlUrl functionTest "[1 2 3]"
        '''
      }
    }
  }
  post {
    always {
      archiveArtifacts artifacts: 'artifacts/**/*', fingerprint: true
      junit allowEmptyResults: true, testResults: 'artifacts/junit/*.xml'
    }
  }
}