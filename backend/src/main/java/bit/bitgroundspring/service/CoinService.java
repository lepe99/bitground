package bit.bitgroundspring.service;


import bit.bitgroundspring.entity.Coin;
import bit.bitgroundspring.repository.CoinRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class CoinService {

    private final CoinRepository coinRepository;
    
    public List<String> getActiveSymbols() {
        return coinRepository.findByIsDeletedFalse()
                .stream()
                .map(Coin::getSymbol)
                .collect(Collectors.toList());
    }
    
    public Optional<Coin> findById(Integer symbolId) {
        return coinRepository.findById(symbolId);
    }
    
}