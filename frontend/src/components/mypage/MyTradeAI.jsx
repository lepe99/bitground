// src/components/MyTradeAI.jsx

import React from 'react';
import '../../styles/MyTradeAI.css'; // 전용 CSS 파일을 임포트합니다.

export default function MyTradeAI({ aiAdvice }) { // aiAdvice prop을 받음
    return (
        <div className="season-ai-box">
            <h2 className="season-ai-title">🔍 AI 기반 시즌 투자 조언</h2>
            {aiAdvice ? ( // aiAdvice 데이터가 있을 경우 렌더링
                <div className="ai-advice-display">
                    <p className="ai-score">AI 점수: <strong>{aiAdvice.score}점</strong></p>
                    <div className="ai-advice-content-text">
                        <h3>AI 조언:</h3>
                        {/* CSS의 white-space: pre-wrap 속성을 이용하여 줄바꿈을 유지 */}
                        <p style={{ whiteSpace: 'pre-wrap' }}>{aiAdvice.advice}</p>
                    </div>
                </div>
            ) : ( // aiAdvice 데이터가 없을 경우 플레이스홀더 렌더링
                <div className="season-ai-placeholder">
                    AI 분석 준비 중이거나, 해당 시즌의 조언이 없습니다.
                </div>
            )}
        </div>
    );
}
