package vn.edu.sales.application.port.out;

import vn.edu.sales.domain.model.Role;

public interface TokenProvider {
    String generate(String email, Role role, int tokenVersion);

    default String generate(String email, Role role) {
        return generate(email, role, 0);
    }
}
