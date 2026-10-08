package com.nexorix.split;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FriendshipRepository extends JpaRepository<Friendship, Long> {

    /** La amistad entre dos personas, sin importar quien la pidio. */
    @Query("select f from Friendship f where (f.requester.id = :a and f.addressee.id = :b) "
            + "or (f.requester.id = :b and f.addressee.id = :a)")
    Optional<Friendship> findBetween(@Param("a") Long a, @Param("b") Long b);

    /** Todas las amistades (aceptadas o pendientes) donde participa la persona. */
    @Query("select f from Friendship f join fetch f.requester join fetch f.addressee "
            + "where f.requester.id = :id or f.addressee.id = :id order by f.createdAt desc")
    List<Friendship> findAllOf(@Param("id") Long id);

    /** Amistades aceptadas entre la persona y cualquiera de los otros (para validar participantes). */
    @Query("select f from Friendship f join fetch f.requester join fetch f.addressee where f.status = 'ACCEPTED' and "
            + "((f.requester.id = :me and f.addressee.id in :others) or (f.addressee.id = :me and f.requester.id in :others))")
    List<Friendship> findAcceptedWith(@Param("me") Long me, @Param("others") Collection<Long> others);
}
