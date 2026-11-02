const API_BASE_URL = 'http://localhost:8090/api/coin';

export const getMarketCodes = async () => {
  try {
    const response = await fetch(`${API_BASE_URL}/market/all`);
    if (!response.ok) throw new Error('Failed to fetch market codes');
    return await response.json();
  } catch (error) {
    console.error('Failed to fetch market codes:', error);
    return [];
  }
};

export const getTicker = async (markets) => {
  try {
    const queryString = markets.join(',');
    const response = await fetch(`${API_BASE_URL}/ticker?markets=${queryString}`);
    if (!response.ok) throw new Error('Failed to fetch ticker');
    return await response.json();
  } catch (error) {
    console.error('Failed to fetch ticker:', error);
    return [];
  }
};

export const getCandles = async (market, count = 200) => {
  try {
    const response = await fetch(`${API_BASE_URL}/candles/minutes/1?market=${market}&count=${count}`);
    if (!response.ok) throw new Error('Failed to fetch candles');
    return await response.json();
  } catch (error) {
    console.error('Failed to fetch candles:', error);
    return [];
  }
}; 