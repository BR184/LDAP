package com.company.idm.infrastructure.startup;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.company.idm.infrastructure.config.AppLdapProperties;
import com.company.idm.infrastructure.config.StartupCheckProperties;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.ldap.core.LdapTemplate;

class EnvironmentStartupVerifierTest {

    @Test
    void acceptsUtf8mb4MysqlSessionEncoding() throws Exception {
        EnvironmentStartupVerifier verifier = verifierWithEncoding("utf8mb4", "utf8mb4", "utf8mb4", "utf8mb4_general_ci");

        assertDoesNotThrow(() -> verifier.run(mock(ApplicationArguments.class)));
    }

    @Test
    void rejectsNonUtf8mb4MysqlSessionEncoding() throws Exception {
        EnvironmentStartupVerifier verifier = verifierWithEncoding("utf8", "utf8", "utf8", "utf8_general_ci");

        assertThatThrownBy(() -> verifier.run(mock(ApplicationArguments.class)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("character set must be utf8mb4");
    }

    @SuppressWarnings("unchecked")
    private EnvironmentStartupVerifier verifierWithEncoding(
        String client,
        String connectionEncoding,
        String results,
        String collation
    ) throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        Statement statement = mock(Statement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(2)).thenReturn(true);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn("MySQL");
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery(org.mockito.ArgumentMatchers.anyString())).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getString(1)).thenReturn(client);
        when(resultSet.getString(2)).thenReturn(connectionEncoding);
        when(resultSet.getString(3)).thenReturn(results);
        when(resultSet.getString(4)).thenReturn(collation);
        AppLdapProperties ldapProperties = new AppLdapProperties();
        StartupCheckProperties startupProperties = new StartupCheckProperties();
        ObjectProvider<LdapTemplate> provider = mock(ObjectProvider.class);
        return new EnvironmentStartupVerifier(dataSource, ldapProperties, startupProperties, provider);
    }
}
