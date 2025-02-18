package com.teamsparta14.order_service.store.repository;

import com.teamsparta14.order_service.store.entity.Store;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StoreRepository extends JpaRepository<Store, UUID> {

    List<Store> findByIsDeletedFalse();

    List<Store> findByCreatedByAndIsDeletedFalse(String createdBy);

    Optional<Store> findById(UUID storeId);
}
