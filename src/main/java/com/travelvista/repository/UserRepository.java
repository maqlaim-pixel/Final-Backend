package com.travelvista.repository;

import com.travelvista.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmailIgnoreCase(String email);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from User u where lower(trim(u.email)) = :email")
    Optional<User> lockByEmail(@org.springframework.data.repository.query.Param("email") String email);
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    Optional<User> findByPhone(String phone);
}
