package br.com.balancecontrol.item;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemBalanceAdjustmentRepository extends JpaRepository<ItemBalanceAdjustment, Long> {

    Page<ItemBalanceAdjustment> findByItemIdOrderByCreatedAtDescIdDesc(Long itemId, Pageable pageable);
}
