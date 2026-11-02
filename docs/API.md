# API 응답 규약

백엔드(Spring Boot) REST API의 성공·실패 응답 형식과 에러 코드 정의입니다.
엔드포인트별 상세 명세는 Swagger UI(`/api/docs/swagger-ui.html`, 관리자 전용)에서 확인할 수 있습니다.

## 1. 성공 응답

별도의 래퍼(`{ success, data }`) 없이 리소스를 그대로 반환하고, 결과는 **HTTP 상태 코드**로 구분합니다.

| 상황 | 상태 코드 | 본문 |
|---|---|---|
| 조회 | `200 OK` | 리소스 DTO 또는 배열 (없으면 빈 배열 `[]`) |
| 생성 | `201 Created` | `{ "id": 12 }` 등 생성 결과 + `Location` 헤더 |
| 수정 | `200 OK` | 수정된 리소스 |
| 삭제·처리 완료 (본문 불필요) | `204 No Content` | 없음 |

- 엔티티를 직접 반환하지 않고 응답 DTO로 변환합니다. (내부 필드·연관 엔티티 노출 방지)
- 로그인 사용자는 요청 본문이나 파라미터가 아니라 JWT에서 꺼냅니다. (`@AuthenticationPrincipal AuthUser`)

### 페이지 응답

목록 페이지는 공통 형식 `PageResponse`를 사용합니다. (`page`는 0부터 시작)

```json
{
  "content": [ ... ],
  "page": 0,
  "size": 10,
  "totalElements": 42,
  "totalPages": 5,
  "last": false
}
```

## 2. 실패 응답 — RFC 9457 Problem Details

모든 에러는 `Content-Type: application/problem+json` 형식으로 응답합니다.
컨트롤러 예외, Spring MVC 표준 예외(404·405·검증 실패 등), Spring Security 필터의 401·403 모두 같은 형식입니다.

```json
{
  "type": "about:blank",
  "title": "Unprocessable Entity",
  "status": 422,
  "detail": "주문 가능 금액이 부족합니다.",
  "instance": "/trade",
  "code": "INSUFFICIENT_CASH",
  "timestamp": "2026-11-02T16:30:00+09:00",
  "traceId": "3f9c2a1b7d4e8f60"
}
```

| 필드 | 설명 |
|---|---|
| `status` | HTTP 상태 코드 |
| `detail` | 사용자에게 보여줄 수 있는 메시지 |
| `code` | 서비스 에러 코드 — 프론트는 이 값으로 분기 |
| `instance` | 요청 경로 |
| `traceId` | 요청 추적 ID — 서버 로그(Loki)에서 이 값으로 해당 요청의 로그를 찾을 수 있음 |
| `errors` | (검증 실패 시) `[{ "field": "title", "message": "제목을 입력해주세요." }]` |

- 예상하지 못한 예외는 `500 INTERNAL_ERROR`로 응답하고, 내부 메시지·스택트레이스는 응답에 넣지 않고 로그로만 남깁니다.
- 프론트는 `src/api/apiError.js`의 `getErrorMessage(error)` / `getErrorCode(error)`로 해석합니다.

### 상태 코드 기준

| 상태 | 의미 |
|---|---|
| 400 | 요청 형식·값이 잘못됨 (검증 실패, 타입 불일치, 깨진 JSON) |
| 401 | 인증 필요 (토큰 없음·만료·위조) |
| 403 | 권한 없음 |
| 404 | 대상 없음 |
| 409 | 현재 상태와 충돌 (이미 처리됨, 진행 중인 시즌 없음 등) |
| 422 | 형식은 맞지만 비즈니스 규칙상 처리 불가 (잔액 부족 등) |
| 5xx | 서버·외부 서비스 오류 |

## 3. 에러 코드

정의: `backend/src/main/java/bit/bitgroundspring/common/error/ErrorCode.java`

| 분류 | 코드 | 상태 | 기본 메시지 |
|---|---|---|---|
| 공통 | `INVALID_INPUT` | 400 | 입력값이 올바르지 않습니다. |
| | `RESOURCE_NOT_FOUND` | 404 | 요청한 리소스를 찾을 수 없습니다. |
| | `METHOD_NOT_ALLOWED` | 405 | 지원하지 않는 HTTP 메서드입니다. |
| | `UNSUPPORTED_MEDIA_TYPE` | 415 | 지원하지 않는 요청 형식입니다. |
| | `INTERNAL_ERROR` | 500 | 서버 오류가 발생했습니다. |
| | `EXTERNAL_API_ERROR` | 502 | 외부 서비스 호출에 실패했습니다. |
| 인증·권한 | `UNAUTHORIZED` | 401 | 로그인이 필요합니다. |
| | `FORBIDDEN` | 403 | 접근 권한이 없습니다. |
| | `REFRESH_TOKEN_MISSING` | 401 | 리프레시 토큰이 없습니다. |
| | `REFRESH_TOKEN_INVALID` | 401 | 리프레시 토큰이 유효하지 않습니다. |
| | `INVALID_SECRET_KEY` | 403 | 유효하지 않은 키입니다. (스케줄러 내부 API) |
| 사용자 | `USER_NOT_FOUND` | 404 | 사용자를 찾을 수 없습니다. |
| 커뮤니티·공지 | `POST_NOT_FOUND` | 404 | 게시글을 찾을 수 없습니다. |
| | `COMMENT_NOT_FOUND` | 404 | 댓글을 찾을 수 없습니다. |
| | `NOTICE_NOT_FOUND` | 404 | 공지사항을 찾을 수 없습니다. |
| | `NOT_AUTHOR` | 403 | 작성자만 처리할 수 있습니다. |
| 시장·시즌 | `COIN_NOT_FOUND` | 404 | 존재하지 않는 코인입니다. |
| | `COIN_DELISTED` | 409 | 거래가 중단된 코인입니다. |
| | `SEASON_NOT_FOUND` | 404 | 시즌을 찾을 수 없습니다. |
| | `NO_ACTIVE_SEASON` | 409 | 진행 중인 시즌이 없습니다. |
| | `SEASON_NOT_COMPLETED` | 409 | 종료된 시즌에서만 요청할 수 있습니다. |
| | `AI_INSIGHT_NOT_FOUND` | 404 | 오늘의 AI 인사이트가 없습니다. |
| | `AI_ADVICE_UNAVAILABLE` | 422 | AI 조언을 생성할 수 없습니다. |
| 즐겨찾기 | `FAVORITE_ALREADY_EXISTS` | 409 | 이미 즐겨찾기에 등록된 코인입니다. |
| | `FAVORITE_NOT_FOUND` | 404 | 즐겨찾기에 없는 코인입니다. |
| 주문 | `INVALID_ORDER` | 400 | 주문 정보가 올바르지 않습니다. |
| | `ORDER_NOT_FOUND` | 404 | 주문을 찾을 수 없습니다. |
| | `ORDER_NOT_PENDING` | 409 | 이미 체결되었거나 취소된 주문입니다. |
| | `INSUFFICIENT_CASH` | 422 | 주문 가능 금액이 부족합니다. |
| | `INSUFFICIENT_ASSET` | 422 | 주문 가능 수량이 부족합니다. |
| | `PRICE_OUT_OF_RANGE` | 422 | 지정가가 시장가에서 너무 벗어났습니다. |
| 파일 | `FILE_EMPTY` | 400 | 파일이 비어 있습니다. |
| | `FILE_TOO_LARGE` | 413 | 파일 크기가 너무 큽니다. |
| | `UNSUPPORTED_FILE_TYPE` | 415 | jpg, png, gif, webp 이미지만 업로드할 수 있습니다. |
| | `FILE_UPLOAD_FAILED` | 500 | 파일 업로드에 실패했습니다. |

## 4. 요청 추적 ID

- 모든 응답에 `X-Trace-Id` 헤더가 붙고, 에러 응답 본문에는 `traceId`로도 들어갑니다.
- 요청에 `X-Trace-Id`(영문·숫자·`-`, 8~64자)를 보내면 그 값을 그대로 이어서 씁니다. 형식이 맞지 않으면 새로 발급합니다.
- 서버 로그에는 `[traceId,userId]`가 함께 남습니다. JSON 로그(`LOGGING_STRUCTURED_FORMAT_CONSOLE=logstash`)에서는 필드로 들어갑니다.

## 5. 인증 흐름 관련 응답

| 엔드포인트 | 성공 | 실패 |
|---|---|---|
| `POST /auth/refresh` | `204` + 새 `jwt_token`·`refresh_token` 쿠키 (Refresh Token Rotation) | `401 REFRESH_TOKEN_MISSING` / `REFRESH_TOKEN_INVALID` + 쿠키 삭제 |
| `POST /auth/logout` | `204` + 쿠키 삭제, 이 기기 세션의 리프레시 토큰 폐기 | — |
| `DELETE /users/me` (탈퇴) | `204` + 쿠키 삭제, 모든 기기 세션 폐기 | — |

- 재발급할 때마다 사용자 존재·탈퇴 여부와 현재 권한을 DB에서 다시 확인합니다. 권한이 바뀌면 다음 재발급부터 반영됩니다.
- **재사용 탐지**: 이미 교체된 리프레시 토큰이 다시 들어오면 탈취로 보고 그 세션(토큰 패밀리) 전체를 폐기합니다.
  교체 후 10초 안에 같은 토큰이 다시 오는 경우(같은 브라우저의 동시 요청)는 세션을 유지하고 새 쿠키 없이 `204`를 돌려줍니다.
- **탈퇴**: 리프레시 토큰을 모두 폐기하고, 이미 발급된 액세스 토큰도 즉시 무효가 됩니다(만료 전이라도 `401`).

프론트 axios 인터셉터는 `401`을 받으면 `/auth/refresh`로 한 번 재발급을 시도하고, 재발급도 실패하면 로그아웃합니다.

## 6. 스케줄러 내부 API

| 엔드포인트 | 인증 | 설명 |
|---|---|---|
| `POST /seasons/update` (`seasonFlag=season\|split`) | `X-Scheduler-Key` 헤더 (공유 키) | Go 스케줄러가 시즌 정산·스플릿 완료를 알림. 실패 시 `403 INVALID_SECRET_KEY` |
