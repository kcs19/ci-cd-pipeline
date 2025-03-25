# ⚙️ CI/CD 환경 구성

## 🚩 개요
GitHub에 변경 사항을 Push하면 자동으로 빌드 후 실행 서버에 배포하고 실행하는 CI/CD 파이프라인을 구축한다.

### 🥅 목표
Jenkins와 GitHub을 연동하여 코드 변경 사항이 발생할 때 다음 프로세스를 자동화한다.

1. **빌드 서버에서 스프링 코드 빌드**
2. **빌드된 파일을 실행 서버로 전송**
3. **애플리케이션 자동 실행**

## 🚀 빌드 서버와 배포 서버를 나누는 이유

### 역할을 분리하여 안정성 보장하기 위해
- 역할을 분리해, 빌드 과정에서 발생하는 부하를 배포 서버에 주지 않아 안정적인 운영 가능
- 문제 발생 시 빌드 서버에서만 점검하여 독립적으로 유지보수 가능

## 🛠️ 기술 스택

| Category            | Technology          |
|---------------------|---------------------|
| 🐳 **Containerization** | docker             |
| ☁️ **CI/CD**            | jekins             |
| ⚙️ **Backend Framework** | spring boot        |
| 🧑‍💻 **Programming Language** | shell          |
| 🐧 **Operating System** | ubuntu             |

---

## 🔧 CI/CD 파이프라인 구성

### 📌 1. GitHub Push 감지
- 개발자가 GitHub에 코드를 푸시하면 Jenkins가 이를 감지하여 빌드를 시작
    - GitHub 이벤트가 발생했을 때, Jenkins에 신호 전달을 위한 Git hook 설정
        
        **페이로드 URL 뒤에 `/github-webhook/` 추가해야 함!**
        **Git 훅은 외부 서비스에 인터넷을 통해 요청을 보내기 때문에 공인 IP 사용해야 함**
        ⇒ `ngrok` 사용하여 IP 포워딩
        
        ![image.png](attachment:9c2dda43-d0f0-4d87-96cc-16bd02f04a27:image.png)

### 📌 2. Jenkins를 통한 파이프라인 빌드
- GitHub 변화 감지 시, Spring Boot 프로젝트를 빌드하여 실행 가능한 JAR 파일 생성
- SSH 키 관리 용이성을 고려하여 VM volume에 저장
    
    ```bash
    pipeline {
        agent any
        
        environment {
            PROJECT_DIR = ''
            JAR_SOURCE = ""
            JAR_DEST = ''
        }
    
        stages {
            stage('Build') {
                steps {
                    // GitHub 리포지토리에서 코드 가져오기
                    git branch: 'main', url: 'https://github.com/  .git'
    
                    // 유닉스 에이전트에서 Maven 실행
                    sh "ls"
                }
            }
            
            stage('Build with Gradle') {
                steps {
                    dir("${PROJECT_DIR}") {
                        sh 'chmod +x gradlew'
                        sh './gradlew build'
                    }
                }
            }
            
            stage('copy volum') {
                steps {
                    dir("${JAR_SOURCE}") {
                        sh 'cp SNAPSHOT.jar ${JAR_DEST}'
                    }
                }
            }
        }
    }
    ```

### 📌 3. 실행 서버로 배포
- `scp`를 사용하여 빌드된 JAR 파일을 실행 서버로 전송
    1. `scp`를 위한 SSH 로그인 자동화 설정
        - 빌드 서버에서 SSH 키 생성 후 공개키 전송
    
    ```bash
    ssh-keygen -t rsa -b 4096 
    ssh-copy-id username@remote_server_ip
    ```
    
    2. 마운트된 저장소 변경 감지 후 실행 서버로 전송
    
    ```bash
    #!/bin/bash
    
    # 변경을 감지할 디렉토리
    WATCH_DIR="$(pwd)/jenkins_volume"
    
    # 원격 서버의 주소와 디렉토리 지정
    REMOTE_USER="ubuntu"
    REMOTE_HOST="prodserver"
    REMOTE_DIR="~"
    
    # inotifywait로 파일 시스템 변경 감지
    inotifywait -m -r -e modify,create,move,delete "$WATCH_DIR" | while read path action file; do
        # 변경된 파일 경로
        FILE_PATH="$path$file"
    
        # 파일이 생성되거나 수정된 경우, scp 실행
        if [[ "$action" == "CREATE" || "$action" == "MODIFY" || "$action" == "MOVED_TO" ]]; then
            echo "변경 감지: $action $FILE_PATH"
            # scp를 사용하여 파일 전송
            scp "$FILE_PATH" "$REMOTE_USER@$REMOTE_HOST:$REMOTE_DIR"
        fi
    done
    ```

### 📌 4. 애플리케이션 자동 실행
- 실행 서버에서 기존 애플리케이션을 종료하고 새 버전 실행
    
    ```bash
    #!/bin/bash
    
    # 변경을 감지할 디렉토리 지정
    WATCH_DIR=$(pwd)
    
    # 최신 버전의 JAR 파일을 찾는 함수
    get_latest_version_jar() {
        local dir="$1"
        find "$dir" -type f -name "*-SNAPSHOT.jar" | sort -V | tail -n 1
    }
    
    # 실행 중인 Java 프로세스를 종료
    kill_running_java_process() {
        local pid=$(ps -ef | grep 'java -jar' | grep -v grep | awk '{print $2}')
        if [[ -n "$pid" ]]; then
            echo "기존 JAR 프로세스를 종료 중: PID $pid"
            kill -9 "$pid"
        else
            echo "실행 중인 JAR 프로세스가 없습니다."
        fi
    }
    
    # inotifywait로 파일 시스템 변경 감지
    inotifywait -m -r -e close_write "$WATCH_DIR" | while read path action file; do
        # 변경된 파일 경로
        FILE_PATH="$path$file"
        echo "$action $FILE_PATH"
    
        # 변경된 파일이 JAR 파일인지 확인
        if [[ "$file" == *-SNAPSHOT.jar ]]; then
            echo "변경 감지: $action $FILE_PATH"
    
            # 실행 중인 JAR 프로세스 종료
            kill_running_java_process
    
            # 최신 버전의 JAR 파일 찾기
            LATEST_JAR=$(get_latest_version_jar "$WATCH_DIR")
    
            if [[ -n "$LATEST_JAR" ]]; then
                echo "최신 JAR 실행: $LATEST_JAR"
                nohup java -jar "$LATEST_JAR" > app.log 2>&1 &
            else
                echo "최신 JAR 파일을 찾을 수 없습니다."
            fi
        fi
    done
    ```

## ➕추가 개선 사항

- VM 하나로 실행 서버 동작 시, 포트 충돌 발생하기 때문에 무중단 배포 불가능
⇒ 요청을 분배하는 Nginx 사용하여 포트 리다이렉션
  Nginx는 여러 서버 간 로드 밸런싱을 제공하며, 포트 충돌 문제를 해결할 수 있을 것으로 생각됨
