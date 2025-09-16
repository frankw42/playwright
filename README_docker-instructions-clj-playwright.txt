Run the `clj-playwright` Docker image

Image: docker.io/frankw0042/clj-playwright

Tags you can use:
- 1.54.0 → immutable release
- stable → last vetted build
- latest → newest build (don’t rely on in CI)
- Or pin by digest: frankw0042/clj-playwright@sha256:<digest>

Prerequisites
- Docker installed and the engine running.
- Public pulls need no login. Push or private pulls require `docker login`.

Quick start (one‑off run)
Windows PowerShell
  From your project root so artifacts land in the repo
  docker run --rm -it --shm-size=1g `
    -v "$PWD:/app" -w /app `
    frankw0042/clj-playwright:stable `
    bash -lc "clj -M -m webtest.core owlUrl functionTest"

Linux/macOS (bash/zsh)
  From your project root
  docker run --rm -it --shm-size=1g     -v "$PWD:/app" -w /app     frankw0042/clj-playwright:stable     bash -lc 'clj -M -m webtest.core owlUrl functionTest'

Passing secrets and options
Examples:
  # add mail creds
  -e MAIL_ID=you@example.com -e MAIL_KEY=****

  # bigger shared memory for Chromium stability (already included above)
  --shm-size=1g

Full example:
  docker run --rm -it --shm-size=1g     -e MAIL_ID=you@example.com -e MAIL_KEY=secret     -v "$PWD:/app" -w /app     frankw0042/clj-playwright:1.54.0     bash -lc 'clj -M -m webtest.core owlUrl functionTest'

Artifacts and logs
- The container writes outputs under /app/artifacts (because you mounted your repo at /app).
- After the run, check ./artifacts/** on the host.

Pull or update the image
  docker pull frankw0042/clj-playwright:stable
  docker pull frankw0042/clj-playwright:1.54.0

Jenkins usage
Option A: Docker agent (plugin)
  pipeline {
    agent {
      docker {
        image 'frankw0042/clj-playwright:1.54.0' // or stable/digest
        args '--shm-size=1g -u root:root'
      }
    }
    stages {
      stage('Checkout') {
        steps {
          checkout scm
          sh 'git submodule update --init --recursive'
        }
      }
      stage('Run tests') {
        steps {
          withCredentials([
            string(credentialsId: 'MAIL_ID',  variable: 'MAIL_ID'),
            string(credentialsId: 'MAIL_KEY', variable: 'MAIL_KEY')
          ]) {
            sh 'clj -M -m webtest.core owlUrl functionTest'
          }
        }
      }
    }
    post { always { archiveArtifacts artifacts: 'artifacts/**/*', allowEmptyArchive: true } }
  }

Option B: Shell docker run (no plugin)
  pipeline {
    agent { label 'docker' } // node with Docker
    stages {
      stage('Checkout'){ steps { checkout scm; sh 'git submodule update --init --recursive' } }
      stage('Run in container') {
        steps {
          withCredentials([
            string(credentialsId: 'MAIL_ID',  variable: 'MAIL_ID'),
            string(credentialsId: 'MAIL_KEY', variable: 'MAIL_KEY')
          ]) {
            sh '''
set -e
docker run --rm --shm-size=1g   -e MAIL_ID -e MAIL_KEY   -v "$PWD:/app" -w /app   frankw0042/clj-playwright:stable   bash -lc 'clj -M -m webtest.core owlUrl functionTest'
'''
          }
        }
      }
    }
    post { always { archiveArtifacts artifacts: 'artifacts/**/*', allowEmptyArchive: true } }
  }

Tagging policy
- Use 1.54.0 or a digest in CI for repeatability.
- Use stable for the last vetted build.
- Avoid relying on latest in CI.

Troubleshooting
- Engine not running: start Docker Desktop; `docker info` should show server details.
- Permission denied: on Linux add your user to the docker group or run with sudo.
- Chromium crashes: keep --shm-size=1g.
- Windows quoting: prefer PowerShell example or escape quotes properly.
- No artifacts: confirm your test writes to ./artifacts under the repo root.
