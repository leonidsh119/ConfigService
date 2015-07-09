package no.cantara.jau;

/**
 * @author <a href="mailto:erik-dev@fjas.no">Erik Drolshammer</a> 2015-07-09.
 */
public interface ServiceConfigDao {
    void addOrUpdateConfig(String clientId, ServiceConfig serviceConfig);
    ServiceConfig findConfig(String clientId);
}
