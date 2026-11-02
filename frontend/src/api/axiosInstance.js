import axios from 'axios';

const isProd = import.meta.env.MODE === 'production';

const axiosInstance = axios.create({
    baseURL: isProd ? 'https://api.bitground.kr' : 'http://localhost:8090',
    timeout: 5000,
    headers: {
        'Content-Type': 'application/json',
    },
});

export default axiosInstance;