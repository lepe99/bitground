import React, {useState} from 'react';
import '../styles/Mypage.css';
import MyTradeInfo from '../components/mypage/MyTradeInfo';
import MyInfo from '../components/mypage/MyInfo';
import MySeasonInfo from '../components/mypage/MySeasonInfo';

export default function Mypage() {
    const [selectedMenu, setSelectedMenu] = useState('invest-info');

    return (
        <div className={"mypage"}>
            <div className="mypage-container">
                <div className={"mypage-title"}>
                    <span>
                    * 마이페이지
                        </span>
                </div>
                <div className={"mypage-content"}>
                    <div className={"mypage-list"}>
                        <div className={`invest-info ${selectedMenu === 'invest-info' ? 'active' : ''}`}
                             onClick={() => setSelectedMenu('invest-info')}>
                            투자 정보
                        </div>
                        <div className={`my-info ${selectedMenu === 'my-info' ? 'active' : ''}`}
                             onClick={() => setSelectedMenu('my-info')}>
                            개인정보 수정
                        </div>
                        {/*<div className={`invest-last ${selectedMenu === 'invest-last' ? 'active' : ''}`}*/}
                        {/*     onClick={() => setSelectedMenu('invest-last')}>*/}
                        {/*    지난시즌 내역*/}
                        {/*</div>*/}
                    </div>

                    <div className={"content-list"}>
                        {selectedMenu === 'invest-info' && (
                            <MyTradeInfo />
                        )}
                        {selectedMenu === 'my-info' && (
                            <MyInfo/>
                        )}
                        {/*{selectedMenu === 'invest-last' && (*/}
                        {/*    <MySeasonInfo/>*/}
                        {/*)}*/}
                    </div>
                </div>
            </div>
        </div>
    );
}