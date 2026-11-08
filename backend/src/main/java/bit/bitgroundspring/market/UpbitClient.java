package bit.bitgroundspring.market;

import bit.bitgroundspring.common.error.BusinessException;
import bit.bitgroundspring.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 업비트 시세 REST API 클라이언트 (시장가 주문의 현재가 조회)
 */
@Component
@RequiredArgsConstructor
public class UpbitClient {

    private final RestTemplate restTemplate;

    @Value("${upbit.api.ticker-url}")
    private String tickerUrl;

    /** 종목의 현재가. float로 받으면 큰 가격에서 자릿수가 잘리므로 응답 원문 그대로 BigDecimal로 읽는다 */
    public BigDecimal currentPrice(String symbol) {
        try {
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    tickerUrl.replace("{symbol}", symbol), HttpMethod.GET, HttpEntity.EMPTY,
                    new ParameterizedTypeReference<>() {
                    });
            List<Map<String, Object>> body = response.getBody();
            if (body == null || body.isEmpty() || body.get(0).get("trade_price") == null) {
                throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "현재가를 조회하지 못했습니다.");
            }
            return new BigDecimal(body.get(0).get("trade_price").toString());
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, e);
        }
    }
}
