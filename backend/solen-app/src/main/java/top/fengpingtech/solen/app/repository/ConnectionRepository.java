package top.fengpingtech.solen.app.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import top.fengpingtech.solen.app.domain.ConnectionDomain;
import top.fengpingtech.solen.app.domain.DeviceDomain;

@Repository
public interface ConnectionRepository extends JpaRepository<ConnectionDomain, String> {
    List<ConnectionDomain> findByDevice(DeviceDomain deviceDomain);
}
