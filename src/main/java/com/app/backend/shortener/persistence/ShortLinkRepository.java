package com.app.backend.shortener.persistence;

import com.app.backend.shortener.domain.ShortLink;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShortLinkRepository extends JpaRepository<ShortLink, Long> {

    Optional<ShortLink> findByCode(String code);

    boolean existsByCode(String code);

    Optional<ShortLink> findFirstByOriginalUrlAndExpiresAtIsNullOrderByIdAsc(String originalUrl);

    Optional<ShortLink> findFirstByOriginalUrlAndExpiresAtOrderByIdAsc(String originalUrl, Instant expiresAt);

    @Modifying
    @Query("update ShortLink l set l.visitCount = l.visitCount + 1 where l.code = :code")
    int incrementVisitCount(@Param("code") String code);
}
