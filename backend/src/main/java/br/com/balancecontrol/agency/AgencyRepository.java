package br.com.balancecontrol.agency;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgencyRepository extends JpaRepository<Agency, Long> {

    Optional<Agency> findByCode(String code);

    // ~250 rows: a sequential scan is cheaper than any index here.
    @Query("select a from Agency a where lower(a.code) like :pattern"
            + " or lower(a.acronym) like :pattern or lower(a.name) like :pattern")
    Page<Agency> search(@Param("pattern") String pattern, Pageable pageable);
}
