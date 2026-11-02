// src/components/CoinTrends/CoinTrends.jsx

import React, { useState, useEffect, useRef } from 'react';
import { fetchNews } from '../api/news'; // '../api/news' 경로 유지
import '../styles/CoinTrends.css'; // 새로운 CSS 파일 임포트
import api from '../api/axiosConfig'; // Axios 인스턴스 임포트 (AI 인사이트를 위해 필요)

import {
  fetchTop5HighTradePriceCoins,
  fetchTop5PriceIncreaseCoins,
  fetchTop5PriceDecreaseCoins,
  fetchWarningCoins,
  fetchAlertCoins,
  fetchTodayInsightSymbols // 오늘자 AI 인사이트 심볼 목록을 가져오는 API 호출 함수
} from '../api/coinService.js';

// 뉴스 검색 드롭다운에 사용되는 인기 코인 목록입니다.
const POPULAR_COINS = ['KRW-BTC', 'KRW-ETH', 'KRW-XRP', 'KRW-SOL'];
const POPULAR_COIN_KEYWORDS = POPULAR_COINS.map(coin => coin.split('-')[1]);

export default function CoinTrends() {
  const [marketData, setMarketData] = useState({
    highTradePrice: [],
    priceIncrease: [],
    priceDecrease: [],
    warningCoins: [],
    alertCoins: [],
  });
  const [newsData, setNewsData] = useState([]);
  const [keyword, setKeyword] = useState('BTC');
  const [currentPage, setCurrentPage] = useState(1);
  const [totalNewsPages, setTotalNewsPages] = useState(1);

  // AI 분석 관련 상태 (드롭다운 선택 및 결과 표시용)
  const [aiInsightSymbols, setAiInsightSymbols] = useState([]); // 드롭다운에 표시될 AI 분석 가능 코인 심볼 목록
  const [selectedCoinSymbol, setSelectedCoinSymbol] = useState(''); // 드롭다운에서 현재 선택된 코인 심볼
  const [selectedCoinAnalysis, setSelectedCoinAnalysis] = useState(null); // 선택된 코인의 AI 분석 결과

  const [loadingMarket, setLoadingMarket] = useState(true);
  const [loadingNews, setLoadingNews] = useState(false);
  const [loadingAnalysis, setLoadingAnalysis] = useState(false); // AI 분석 데이터 (드롭다운 목록 및 선택된 코인) 로딩

  const newsCardRef = useRef(null);

  // AI 분석 드롭다운 목록 구성 (고객님 요청에 따라 특정 코인만 포함)
  // 이 useEffect는 컴포넌트 마운트 시 한 번만 실행되어 드롭다운 목록을 구성하고 기본값을 설정합니다.
  useEffect(() => {
    const fetchAiInsightSymbolsAndSetInitial = async () => {
      setLoadingAnalysis(true); // AI 분석 관련 전체 로딩 시작
      try {
        // 백엔드에서 오늘자 AI 분석을 가진 '모든' 코인 심볼 목록을 가져옵니다.
        let availableInsightSymbolsFromBackend = await fetchTodayInsightSymbols();

        // 고객님 요청에 따른 AI 분석 드롭다운의 고정 표시 순서 및 목록 정의
        const preferredFixedSymbols = ['MARKET_OVERALL', 'KRW-BTC', 'KRW-ETH', 'KRW-SOL', 'KRW-XRP'];

        let finalDropdownSymbols = [];
        const addedSymbols = new Set(); // 중복 추가 방지를 위한 Set

        // 1. 고정적으로 표시되어야 하는 심볼을 우선적으로 추가합니다.
        for (const symbol of preferredFixedSymbols) {
          const insight = availableInsightSymbolsFromBackend.find(s => s.symbol === symbol);
          if (insight) {
            finalDropdownSymbols.push(insight);
            addedSymbols.add(symbol);
          }
        }

        // 2. 고정 목록에 없는, AI가 추천한 나머지 코인들을 추가합니다.
        // 이제 KRW-DOGE 제외 로직은 없습니다.
        const recommendedCoins = availableInsightSymbolsFromBackend.filter(insight =>
            !addedSymbols.has(insight.symbol)
        ).sort((a, b) => a.koreanName.localeCompare(b.koreanName)); // 한글 이름 기준으로 정렬

        // 추천 코인 중 최대 5개까지만 추가
        for (let i = 0; i < Math.min(5, recommendedCoins.length); i++) {
          finalDropdownSymbols.push(recommendedCoins[i]);
        }

        setAiInsightSymbols(finalDropdownSymbols);

        // 드롭다운 기본 선택 및 해당 AI 분석 결과 설정
        if (finalDropdownSymbols.length > 0) {
          const defaultSelectedSymbol = 'MARKET_OVERALL';
          const initialSymbol = finalDropdownSymbols.find(option => option.symbol === defaultSelectedSymbol)
              ? defaultSelectedSymbol
              : finalDropdownSymbols[0].symbol; // 전체 시장이 없으면 첫 번째 옵션 선택

          setSelectedCoinSymbol(initialSymbol);

          // 초기 로딩 시 기본 선택된 심볼의 AI 분석 데이터도 함께 가져옴
          const initialInsightRes = await api.get(`/coins/${initialSymbol}/insight`); //
          setSelectedCoinAnalysis(initialInsightRes.data);
        } else {
          setSelectedCoinAnalysis(null);
        }

      } catch (error) {
        console.error("AI 분석 드롭다운 목록 및 초기 분석 데이터 구성 중 오류 발생:", error);
        setAiInsightSymbols([]);
        setSelectedCoinAnalysis(null);
      } finally {
        setLoadingAnalysis(false); // AI 분석 관련 전체 로딩 완료
      }
    };

    fetchAiInsightSymbolsAndSetInitial();
  }, []); // 컴포넌트 마운트 시 한 번만 실행

  // 드롭다운에서 선택된 특정 코인에 대한 AI 분석 가져오기
  // 이 useEffect는 selectedCoinSymbol이 변경될 때마다 해당 코인의 분석 결과를 다시 불러옵니다.
  useEffect(() => {
    const getSelectedCoinInsight = async () => {
      if (!selectedCoinSymbol) {
        setSelectedCoinAnalysis(null);
        return;
      }
      setLoadingAnalysis(true); // 선택 변경 시 로딩 시작
      try {
        const insightRes = await api.get(`/coins/${selectedCoinSymbol}/insight`); // /api/ 접두사 제거
        setSelectedCoinAnalysis(insightRes.data);
      } catch (error) {
        console.error(`선택된 코인(${selectedCoinSymbol})의 AI 인사이트를 불러오는 데 실패했습니다:`, error);
        setSelectedCoinAnalysis(null);
      } finally {
        setLoadingAnalysis(false); // 선택 변경 시 로딩 완료
      }
    };
    // aiInsightSymbols가 로드된 후에만 getSelectedCoinInsight 호출
    if (aiInsightSymbols.length > 0 && selectedCoinSymbol) {
      getSelectedCoinInsight();
    }
  }, [selectedCoinSymbol, aiInsightSymbols]); // aiInsightSymbols가 로드되어야 selectedCoinSymbol이 유효하게 설정될 수 있음

  // 마켓 데이터 (거래대금, 상승/하락폭, 유의/주의 종목) 가져오기
  useEffect(() => {
    const getMarketData = async () => {
      setLoadingMarket(true);
      try {
        const [highTradePrice, priceIncrease, priceDecrease, warningCoins, alertCoins] = await Promise.all([
          fetchTop5HighTradePriceCoins(),
          fetchTop5PriceIncreaseCoins(),
          fetchTop5PriceDecreaseCoins(),
          fetchWarningCoins(),
          fetchAlertCoins(),
        ]);
        setMarketData({ highTradePrice, priceIncrease, priceDecrease, warningCoins, alertCoins });
      } catch (error) {
        console.error("마켓 데이터를 불러오는 데 실패했습니다:", error);
        setMarketData({
          highTradePrice: [],
          priceIncrease: [],
          priceDecrease: [],
          warningCoins: [],
          alertCoins: [],
        });
      } finally {
        setLoadingMarket(false);
      }
    };
    getMarketData();

    // 1분마다 데이터 갱신
    const interval = setInterval(getMarketData, 60000);
    return () => clearInterval(interval);
  }, []);

  // 뉴스 데이터 가져오기 (키워드 또는 페이지 변경 시)
  useEffect(() => {
    const getNews = async () => {
      setLoadingNews(true);
      try {
        const newsResponse = await fetchNews(keyword, currentPage);
        setNewsData(newsResponse.newsData);
        setTotalNewsPages(newsResponse.totalPage);
      } catch (error) {
        console.error("뉴스 데이터를 불러오는 데 실패했습니다:", error);
        setNewsData([]);
        setTotalNewsPages(1);
      } finally {
        setLoadingNews(false);
        if (newsCardRef.current) {
          newsCardRef.current.scrollTop = 0;
        }
      }
    };
    if (keyword) {
      getNews();
    }
  }, [keyword, currentPage]);

  const handleKeywordChange = (e) => {
    setKeyword(e.target.value);
    setCurrentPage(1);
  };

  const handlePopularCoinSelect = (e) => {
    setKeyword(e.target.value);
    setCurrentPage(1);
  };

  const handlePageChange = (newPage) => {
    if (newPage > 0 && newPage <= totalNewsPages) {
      setCurrentPage(newPage);
    }
  };

  const handleCoinSelectForAnalysis = (e) => {
    setSelectedCoinSymbol(e.target.value);
  };

  return (
      <div className="trends-container">

        {/* -------------------- 첫 번째 줄 -------------------- */}

        {/* === AI 분석 카드 (드롭다운 선택 코인) === */}
        <div className="trend-card bitcoin-analysis">
          <div className="card-header">
            <h3 className="card-title">AI 코인 분석</h3>
            <div className="coin-select-wrapper">
              <select
                  className="popular-coin-select"
                  value={selectedCoinSymbol}
                  onChange={handleCoinSelectForAnalysis}
                  disabled={loadingAnalysis || aiInsightSymbols.length === 0}
              >
                {aiInsightSymbols.length === 0 ? (
                    <option value="">분석 코인 불러오는 중...</option>
                ) : (
                    aiInsightSymbols.map((coinDto) => (
                        <option key={coinDto.symbol} value={coinDto.symbol}>
                          {coinDto.koreanName} ({coinDto.symbol.replace('KRW-', '')})
                        </option>
                    ))
                )}
              </select>
            </div>
          </div>
          <div className="card-content">
            {loadingAnalysis ? (
                <div className="analysis-loading">AI가 분석 중입니다...</div>
            ) : selectedCoinAnalysis ? (
                <div className="ai-analysis-results">
                  <p className="analysis-text">{selectedCoinAnalysis.insight}</p>
                  <div className="analysis-meta">
                  <span className={`analysis-score ${selectedCoinAnalysis.score >= 60 ? 'positive' : 'negative'}`}> {/* 60점 이상이면 positive */}
                    점수: {selectedCoinAnalysis.score}
                  </span>
                    <span className="analysis-date">
                    분석일: {new Date(selectedCoinAnalysis.date).toLocaleDateString('ko-KR')}
                  </span>
                  </div>
                </div>
            ) : (
                <div className="no-results">선택된 코인에 대한 AI 분석이 없습니다.</div>
            )}
          </div>
        </div>

        {/* === 거래대금 TOP 5 === */}
        <div className="trend-card high-volume">
          <div className="card-header">
            <h3 className="card-title">거래대금 TOP 5</h3>
          </div>
          <div className="card-content">
            {loadingMarket ? (
                <div className="loading-text">데이터 불러오는 중...</div>
            ) : marketData.highTradePrice.length > 0 ? (
                <ul className="coin-list">
                  {marketData.highTradePrice.map((coin, index) => (
                      <li key={index} className="coin-item">
                        <span className="coin-name">{coin.koreanName} <span className="coin-symbol">({coin.symbol})</span></span>
                        <span className="coin-value">{new Intl.NumberFormat('ko-KR').format(coin.tradePrice24h)}원</span>
                      </li>
                  ))}
                </ul>
            ) : (
                <div className="no-results">데이터가 없습니다.</div>
            )}
          </div>
        </div>

        {/* -------------------- 두 번째 줄 (기존) -------------------- */}

        {/* === 최신 뉴스 === */}
        <div className="trend-card latest-news">
          <div className="card-header">
            <h3 className="card-title">최신 뉴스</h3>
            <div className="news-search-controls">
              <input
                  type="text"
                  placeholder="뉴스 검색 키워드"
                  className="news-search-input"
                  value={keyword}
                  onChange={handleKeywordChange}
                  onKeyPress={(e) => {
                    if (e.key === 'Enter') {
                      setCurrentPage(1);
                    }
                  }}
              />
              <select
                  className="popular-coin-select"
                  value={keyword}
                  onChange={handlePopularCoinSelect}
              >
                {POPULAR_COIN_KEYWORDS.map((kw) => (
                    <option key={kw} value={kw}>{kw}</option>
                ))}
              </select>
            </div>
          </div>
          <div className="card-content" ref={newsCardRef}>
            {loadingNews ? (
                <div className="loading-text">뉴스 불러오는 중...</div>
            ) : newsData.length > 0 ? (
                <ul className="news-list">
                  {newsData.map((news, index) => (
                      <li key={index} className="news-item">
                        <a href={news.link} target="_blank" rel="noopener noreferrer" className="news-title" dangerouslySetInnerHTML={{ __html: news.title }}></a>
                        <span className="news-date">{new Date(news.pubDate).toLocaleDateString('ko-KR')}</span>
                      </li>
                  ))}
                </ul>
            ) : (
                <div className="no-results">검색된 뉴스가 없습니다.</div>
            )}
          </div>
          <div className="news-pagination">
            <button onClick={() => handlePageChange(currentPage - 1)} disabled={currentPage === 1}>이전</button>
            <span>{currentPage} / {totalNewsPages}</span>
            <button onClick={() => handlePageChange(currentPage + 1)} disabled={currentPage === totalNewsPages}>다음</button>
          </div>
        </div>

        {/* === 상승폭 큰 종목 === */}
        <div className="trend-card price-increase">
          <div className="card-header">
            <h3 className="card-title">상승폭 큰 종목</h3>
          </div>
          <div className="card-content">
            {loadingMarket ? (
                <div className="loading-text">데이터 불러오는 중...</div>
            ) : marketData.priceIncrease.length > 0 ? (
                <ul className="coin-list">
                  {marketData.priceIncrease.map((coin, index) => (
                      <li key={index} className="coin-item">
                        <span className="coin-name">{coin.koreanName} <span className="coin-symbol">({coin.symbol})</span></span>
                        <span className="coin-value positive">+{coin.changeRate.toFixed(2)}%</span>
                      </li>
                  ))}
                </ul>
            ) : (
                <div className="no-results">데이터가 없습니다.</div>
            )}
          </div>
        </div>

        {/* -------------------- 세 번째 줄 (기존) -------------------- */}

        {/* === 거래유의 종목 === */}
        <div className="trend-card caution-trading">
          <div className="card-header">
            <h3 className="card-title">거래유의 종목</h3>
          </div>
          <div className="card-content">
            {loadingMarket ? (
                <div className="loading-text">데이터 불러오는 중...</div>
            ) : marketData.warningCoins.length > 0 ? (
                <ul className="coin-list">
                  {marketData.warningCoins.map((coin, index) => (
                      <li key={index} className="coin-item">
                        <span className="coin-name">{coin.koreanName} <span className="coin-warning-item">({coin.symbol})</span></span>
                        <span className="coin-warning">⛔️ 유의</span>
                      </li>
                  ))}
                </ul>
            ) : (
                <div className="no-results">현재 거래유의 종목이 없습니다.</div>
            )}
          </div>
        </div>

        {/* === 투자주의 종목 === */}
        <div className="trend-card alert-trading">
          <div className="card-header">
            <h3 className="card-title">투자주의 종목</h3>
          </div>
          <div className="card-content">
            {loadingMarket ? (
                <div className="loading-text">데이터 불러오는 중...</div>
            ) : marketData.alertCoins.length > 0 ? (
                <ul className="coin-list">
                  {marketData.alertCoins.map((coin, index) => (
                      <li key={index} className="coin-item">
                        <span className="coin-name">{coin.koreanName} <span className="coin-warning-item">({coin.symbol})</span></span>
                        <span className="coin-warning">⚠️ 주의</span>
                      </li>
                  ))}
                </ul>
            ) : (
                <div className="no-results">현재 투자주의 종목이 없습니다.</div>
            )}
          </div>
        </div>

        {/* === 하락폭 큰 종목 === */}
        <div className="trend-card price-decrease">
          <div className="card-header">
            <h3 className="card-title">하락폭 큰 종목</h3>
          </div>
          <div className="card-content">
            {loadingMarket ? (
                <div className="loading-text">데이터 불러오는 중...</div>
            ) : marketData.priceDecrease.length > 0 ? (
                <ul className="coin-list">
                  {marketData.priceDecrease.map((coin, index) => (
                      <li key={index} className="coin-item">
                        <span className="coin-name">{coin.koreanName} <span className="coin-symbol">({coin.symbol})</span></span>
                        <span className="coin-value negative">{coin.changeRate.toFixed(2)}%</span>
                      </li>
                  ))}
                </ul>
            ) : (
                <div className="no-results">데이터가 없습니다.</div>
            )}
          </div>
        </div>
      </div>
  );
}