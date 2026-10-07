package vn.edu.sales.application.port.out;

import vn.edu.sales.domain.model.Role;

public interface TokenProvider {
    String generate(String email, Role role);
}
