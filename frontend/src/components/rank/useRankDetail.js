import {useEffect, useState} from 'react';
import {fetchUserRankDetail} from '../../api/rankApi.js';

// 같은 사용자를 다시 열거나 다시 올려 보면 서버에 다시 묻지 않는다 (진행 중인 요청도 함께 쓴다)
const cache = new Map();

const load = (userId) => {
    if (!cache.has(userId)) {
        cache.set(userId, fetchUserRankDetail(userId).catch((error) => {
            console.error('참가자 정보 조회 실패:', error);
            cache.delete(userId);
            return {highestTier: null, pastSeasonTiers: []};
        }));
    }
    return cache.get(userId);
};

/** 참가자의 최고 티어와 지난 5시즌 티어. 불러오는 중이면 null */
export function useRankDetail(userId) {
    const [detail, setDetail] = useState({userId: null, value: null});

    useEffect(() => {
        if (userId == null) return;
        let alive = true;
        load(userId).then((value) => alive && setDetail({userId, value}));
        return () => {
            alive = false;
        };
    }, [userId]);

    return detail.userId === userId ? detail.value : null;
}
