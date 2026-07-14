# practice-sns

Java와 Spring Boot 학습을 위해 만든 간단한 SNS 프로젝트입니다.
RESTful API(`/api/**`)와 Thymeleaf 서버사이드 렌더링을 함께 사용합니다.

## 🛠 기술 스택

- **Java 21**, **Spring Boot 3.4.5**, Gradle
- Spring Data JPA + **MySQL**
- Spring Security + **JWT**(jjwt 0.12.5) — httpOnly 쿠키 기반 인증
- Thymeleaf, Lombok

---

## 📌 주요 기능

### 👤 사용자

#### 🟢 회원가입 ✅
- `id`, `password`를 전달받아 처리
- `id` 조건
  - 1~30자
  - 영어 대소문자, 숫자, `.`, `_` 사용 가능
  - 숫자로만 구성될 수 없음
  - 연속된 `.` 불가, `.`로 시작하거나 끝날 수 없음
- `password` 조건
  - 8~15자
  - 대소문자, 숫자, 특수문자 포함 필수
- `id` 중복 확인 후 DB에 저장

#### 🔐 로그인 / 로그아웃 ✅
- `id`, `password` 검증 후 JWT 토큰 발급
- 토큰은 **httpOnly 쿠키(`JWT_TOKEN`)** 로 내려가며, 이후 모든 요청에 자동으로 실려 인증에 사용됨
- 로그아웃 시 쿠키를 만료시켜 로그아웃 처리

#### 🧑 프로필 기능 ✅
- 프로필 이미지, 닉네임 등 기본 정보 확인
- 닉네임 수정 가능 (본인 프로필에서만 수정 버튼 노출)
- 프로필 하단에 해당 사용자가 작성한 게시물 피드 표시

#### ❌ 회원탈퇴 기능 ✅
- 로그인한 사용자가 자신의 계정을 삭제
- 탈퇴 시 관련 데이터 **모두 삭제** (게시물 / 댓글 / 좋아요 기록)
- 탈퇴 후 쿠키 만료로 자동 로그아웃 처리

---

### 📝 게시물 / 💬 댓글 / ❤️ 좋아요

#### 게시물 ✅
- **제목 없이 내용만**으로 작성 (인스타그램·X 스타일)
- 작성 / 조회 / 수정 / 삭제
- 수정은 작성자 본인만, 삭제는 작성자 본인 또는 관리자
- 전체 게시물 피드 조회, 피드에서 게시물 클릭 시 모달로 상세 보기
- 모달에서 작성자 닉네임 클릭 시 해당 작성자 프로필로 이동

#### 댓글 ✅
- 게시물에 댓글 추가
- 수정은 작성자 본인만, 삭제는 작성자 본인 또는 관리자
- 게시물의 전체 댓글 조회

#### 좋아요 ✅
- 게시물 및 댓글에 좋아요 추가 / 취소

---

### 🛠️ 관리자 기능

#### 게시물 / 댓글 삭제 ✅
- 관리자는 모든 게시물·댓글을 삭제할 수 있음

#### 사용자 정지 ✅
- **프로필 페이지에서 닉네임 클릭** → 정지 기간 선택 후 정지
  - 정지 기간: 1일, 3일, 7일, 30일, 90일, 영구 정지
- 정지된 사용자의 콘텐츠는 **삭제하지 않고 가림(마스킹)** 처리
  - 피드: 정지 사용자의 게시물은 목록에서 제외
  - 게시물 상세 / 댓글: "이용이 정지된 유저입니다"로 표시하여 원문 노출 방지
  - 프로필: 피드 영역에 정지 안내 문구 표시
- 정지 기간이 지나면 **자동으로 원래대로 복구** (별도 해제 작업 불필요)
- 정지된 계정은 로그인 시 정지 안내와 함께 차단

---

## 🔑 인증 방식

- 로그인 성공 시 서버가 JWT를 발급해 **httpOnly 쿠키**로 설정
- 서버의 `JwtAuthFilter`와 각 API가 쿠키의 토큰을 읽어 인증 처리
- 클라이언트(브라우저)는 토큰을 직접 보관하지 않으며, 로그인이 필요한 페이지(예: 게시글 작성)는 서버에서 접근을 제어

---

## ▶️ 실행 방법

1. MySQL에 데이터베이스 생성
   ```sql
   CREATE DATABASE sns_db CHARACTER SET utf8mb4;
   ```

2. `src/main/resources/application.properties` 작성 (git에 포함되지 않음)
   ```properties
   spring.application.name=sns

   spring.datasource.url=jdbc:mysql://localhost:3306/sns_db?serverTimezone=UTC&characterEncoding=UTF-8
   spring.datasource.username=<사용자명>
   spring.datasource.password=<비밀번호>
   spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

   spring.jpa.hibernate.ddl-auto=update
   spring.jpa.show-sql=true
   spring.thymeleaf.cache=false

   # HS256 서명용 시크릿 (최소 32바이트 이상)
   jwt.secret=<시크릿_키>
   ```

3. 애플리케이션 실행
   ```bash
   ./gradlew bootRun
   ```
   실행 후 `http://localhost:8080` 접속
