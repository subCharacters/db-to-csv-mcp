# Repository Guidelines

## 프로젝트 구조 및 모듈 구성
- 주요 소스는 `src/main/java/com/subcharacter/db_to_csv_mcp`에 위치하며, `DbToCsvMcpApplication`이 MCP 툴 목록을 등록하고 `QueryService`가 SQL 검증과 CSV 직렬화를 담당합니다.
- 런타임 설정은 `src/main/resources/application.properties`, 데모 스키마와 시드 데이터는 `data.sql`에 있으므로 스키마 변경 시 두 파일을 함께 업데이트하세요.
- 테스트는 `src/test/java`에서 본 패키지 구조와 동일하게 배치합니다. Gradle 산출물은 `build/`에, STDIO 분리를 위한 로그는 `logs/db-to-csv-mcp-server.log`에 저장됩니다.

## 빌드·테스트·개발 명령어
- `./gradlew bootRun`은 STDIO 기반 Spring Boot MCP 서버를 무인 모드로 기동해 도구 트래픽을 중계합니다.
- `./gradlew test`는 JUnit 5 테스트를 실행하므로 PR 전 SQL 엣지 케이스를 확인하는 기본 절차로 사용하세요.
- `./gradlew build`는 컴파일과 테스트를 수행하고 실행 가능한 JAR을 `build/libs/`에 생성합니다.
- `./gradlew clean`은 빌드 산출물을 정리하여 클래스패스 문제 재현이나 환경 초기화에 활용합니다.

## 코딩 스타일 및 네이밍 규칙
- Gradle 툴체인 설정대로 Java 17을 타깃하며, 들여쓰기는 스페이스 4칸과 K&R 중괄호 배치를 기본으로 합니다.
- Spring 컴포넌트는 `com.subcharacter.db_to_csv_mcp` 패키지 아래에 두고, 새로운 MCP 툴은 `generateReportTool`처럼 동사 기반의 명령형 이름을 부여하세요.
- 변경 가능성이 낮은 로컬 변수는 불변으로 유지하고, `fetchItemsAsCsv`처럼 의도가 드러나는 메서드명을 사용하며 특별한 검증 흐름은 짧은 주석으로 남깁니다.
- 의미 있는 이름만 사용하라
- 함수의 인자는 3개이하로 해라. 4개이상이 된다면 객체로 묶거나 Builder를 사용.
- 한 함수가 여러 상태를 변경하는 것을 최소한으로 한다.
- 테스트 가능한 구조로 작성하라.

### 코딩 규칙
- 모든 반복문은 명확한 범위와 종료 조건을 가져야 한다. while(true)와 같은 비결정적 루프는 금지.
- 하나의 메서드는 하나의 책임만 가진다.
- 복잡한 로직은 명시적으로 분리한다. 복잡한 if문은 전략패턴 혹은 Enum으로 분리한다. 3단 이상의 if중첩은 금지.
- static과 singleton은 최소화한다.
- DB, API, 파일과 같은 외부자원 접근은 예외처리와 타임아웃을 갖는다.
- 모든 입력은 명시적으로 검증한다. @NotNull과 같은 Validation을 적극 활용.
- 재귀호출 대신 명시적 반복을 사용한다.
- 동적 리소스 생성을 최소화하고 의존성 주입으로 관리한다.
- 모든 분기는 기본 case를 갖는다. switch라면 무조건 default를 갖는다.
- 모든 컴파일, 테스트 경고는 에러로 간주한다.

## 테스트 가이드라인
- 테스트 프레임워크는 JUnit 5(`spring-boot-starter-test`)이며, 테스트 클래스 이름은 `*Tests` 패턴을 따르고 본 코드와 동일한 패키지를 맞춥니다.
- `QueryService`는 H2 기반 통합 테스트나 `@JdbcTest` 슬라이스로 검증해 CSV 헤더 순서와 값 포맷을 보장하세요.
- SELECT 이외의 동사를 차단하는 음수 시나리오와 예외 메시지를 함께 검증하여 방어 로직이 유지되는지 확인합니다.

## 커밋 및 PR 가이드라인
- 사용자의 요청이 없는 한 아무것도 하지 않는다.

## MCP 및 데이터베이스 참고 사항
- `spring.ai.mcp.server.tool-response-mime-type.executeQuery`는 실제 응답 형식과 일치해야 하므로 CSV 외 포맷으로 전환 시 반드시 갱신하세요.
- 데모 데이터는 메모리 H2로 제공되며, 외부 데이터 소스를 추가할 경우 `application.properties`와 이 가이드에 자격 증명과 연결 문자열을 문서화합니다.
