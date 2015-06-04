package com.scytl.multi;

import javax.annotation.Resource;
import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

@Stateless
public class RoutedPersister {

    @PersistenceContext(unitName = "router")
    private EntityManager em;

    @Resource(name = "My Router", type = MultitenancyRouter.class)
    private MultitenancyRouter router;

    public void persist(String name, String ds) {
        router.setTenant(ds);
        Person p = new Person();
        p.setName(name);
        em.persist(p);
    }
}
