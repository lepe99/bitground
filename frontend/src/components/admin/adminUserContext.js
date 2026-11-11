import {createContext, useContext} from 'react';

// 관리자 페이지 어디서든 사용자 상세 창을 연다 (닉네임은 겹칠 수 있어 번호로 연다)
// version: 정지·해제처럼 사용자 정보가 바뀌면 올라가, 목록이 다시 읽게 한다
export const AdminUserContext = createContext({openUser: () => {}, version: 0});

export const useAdminUser = () => useContext(AdminUserContext);
