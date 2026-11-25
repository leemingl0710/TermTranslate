
-- =========================================
-- 외국인 유학생 전공용어 학습 지원 시스템
-- 서버 및 데이터베이스 담당
-- =========================================

-- 1. 데이터베이스 생성
CREATE DATABASE IF NOT EXISTS student_learning_db
CHARACTER SET utf8mb4;

-- 2. 데이터베이스 선택
USE student_learning_db;


-- =========================================
-- 3. 전공용어 테이블
-- =========================================

CREATE TABLE IF NOT EXISTS terms (
    term_id INT AUTO_INCREMENT PRIMARY KEY,
    korean_term VARCHAR(100) NOT NULL,
    english_term VARCHAR(100),
    explanation TEXT
);


-- =========================================
-- 4. 회원 정보 테이블
-- =========================================

CREATE TABLE IF NOT EXISTS users (
    user_id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);


-- =========================================
-- 5. 업로드 문서 정보 테이블
-- =========================================

CREATE TABLE IF NOT EXISTS documents (
    document_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(500),
    uploaded_at DATETIME DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (user_id)
        REFERENCES users(user_id)
);


-- =========================================
-- 6. AI 분석 결과 테이블
-- =========================================

CREATE TABLE IF NOT EXISTS analysis_results (
    result_id INT AUTO_INCREMENT PRIMARY KEY,
    document_id INT NOT NULL,
    translated_text LONGTEXT,
    explanation LONGTEXT,
    summary LONGTEXT,
    analyzed_at DATETIME DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (document_id)
        REFERENCES documents(document_id)
);


-- =========================================
-- 7. API 사용 로그 테이블
-- =========================================

CREATE TABLE IF NOT EXISTS api_logs (
    log_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT,
    request_type VARCHAR(100),
    status VARCHAR(30),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (user_id)
        REFERENCES users(user_id)
);


-- =========================================
-- 8. 테이블 목록 확인
-- =========================================

SHOW TABLES;

-- 9. 테이블 구조 확인
USE student_learning_db;

DESCRIBE users;
DESCRIBE terms;
DESCRIBE documents;
DESCRIBE analysis_results;
DESCRIBE api_logs;


-- 10. 외래키 연결 상태 확인
USE student_learning_db;

SELECT
    TABLE_NAME,
    COLUMN_NAME,
    REFERENCED_TABLE_NAME,
    REFERENCED_COLUMN_NAME
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = 'student_learning_db'
  AND REFERENCED_TABLE_NAME IS NOT NULL;

