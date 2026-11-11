// 시즌 규칙. 같은 값이 스케줄러(scheduler/service/rules.go)와 백엔드(InitialCashPolicy.java)에도 있다.
// 같은지는 scheduler/service/rules_test.go가 확인한다
import {formatNumber} from '../utils/format.js';

/** 스플릿 2 시작 때 추가로 주는 현금 */
const SPLIT_CASH = 10_000_000;

/** '1,000만원' */
export const SPLIT_CASH_LABEL = `${formatNumber(SPLIT_CASH / 10_000)}만원`;
