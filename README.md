# Playwright Clojure Harness/Framework

End-to-end UI tests in Clojure with Playwright.  Makes use of Clojure as a powerful scripting language.
Provides a framework that wraps test steps with setup, logging and cleanup processes.

Produces EDN and XML logs. Takes screenshots at point of failure. 
 - Runs locally or via Docker. 
 - Jenkins compatible
 - Optionally runs headless or headed. 
 - Optionally send email with a pass/fail report and logs attached.
 - Optionally can run with a GUI frontend. 

Intended as a example demo:
  The program contains a test suite for the Owl Buddy webpage App. 
  Live access to the Owl Buddy app at: https://frankw42.github.io/public/index.html.
  Video of automated test run at: https://drive.google.com/file/d/1OccvxvJJbDX7qbDu0GBRkRpyDszYDf7s/view?usp=sharing

## Features
- Selective test execution
- Headed/headless, Chromium/Firefox/WebKit
- EDN logs in: /tmp/logs
- Junit test report in: /tmp/junit
- Test report in: /tmp/artifacts
- Screenshots in: /tmp/screenshots
- Download files in: /tmp/downloads
- Simple env-driven config

## Quick start
```bash
  #clj -M -m webtest.core <url> <suite>
# example:  
clojure -M -m webtest.core owlUrl functionTest "[1 2]" 
```

## Example Test Run - Console output
Starting Playwright-based test...   version: 1.0.3 Current time is: 2025-09-13T21:34:44.691236300Z

Time:   05:34 PM 

*** Suite Name::  Function Test
[SETUP] browser: :chromium headless? false artifacts: /tmp/artifacts downloads: /tmp/downloads
        Navigating to: https://frankw42.github.io/public/index.html
[OK  ] 001-Title-should-be:-Owl-Buddy - Title should be: Owl Buddy
[OK  ] 002-Upload:-resources/owlBuddycloudinary.json - Upload: resources/owlBuddycloudinary.json
[OK  ] 003-Start-flipbook-animation - Start flipbook animation
[OK  ] 004-Stop-flipbook-animation - Stop flipbook animation
[OK  ] 005-Download-test - Download test
[OK  ] 006-Select-image-by-category - Select image by category
[OK  ] 007-Select-image-by-category - Select image by category
[OK  ] 008-Select-image-by-category - Select image by category
[OK  ] 009-Select-image-by-category - Select image by category
[OK  ] 010-Select-music-track-by-category - Select music track by category
[OK  ] 011-Start-flipbook-animation - Start flipbook animation
[OK  ] 012-Stop-flipbook-animation - Stop flipbook animation
[OK  ] 013-Show-Info-panel - Show Info panel
[OK  ] 014-Hide-Info-panel - Hide Info panel
[OK  ] 015-Download-test - Download-test
[OK  ] 016-Start-flipbook-animation - Start flipbook animation
[OK  ] 017-Start-tilt-animation - Start tilt animation
[OK  ] 018-Select-image-by-category - Select image by category
[OK  ] 019-Select-image-by-category - Select image by category
[OK  ] 020-Select-image-by-category - Select image by category
[OK  ] 021-Select-image-by-category - Select image by category
[OK  ] 022-Select-music-by-category - Select music by category
[OK  ] 023-Select-music-by-category - Select music by category
[OK  ] 024-Select-music-by-category - Select music by category
Email sent: Owl Test - Function Test, Passed (24 of 24)
[CLEANUP] done

## 
