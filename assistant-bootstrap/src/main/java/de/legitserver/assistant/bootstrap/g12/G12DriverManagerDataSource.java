package de.legitserver.assistant.bootstrap.g12;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;
import javax.sql.DataSource;

/** Minimal pool-free DataSource for the Assistant runtime; PostgreSQL remains the connection authority. */
final class G12DriverManagerDataSource implements DataSource {
    private final String url;
    private final String user;
    private final String password;

    G12DriverManagerDataSource(String url, String user, String password) {
        if (url == null || url.isBlank() || user == null || user.isBlank()) {
            throw new IllegalArgumentException("PostgreSQL URL/user required");
        }
        this.url = url.strip();
        this.user = user.strip();
        this.password = password == null ? "" : password;
    }

    @Override public Connection getConnection() throws SQLException { return DriverManager.getConnection(url, user, password); }
    @Override public Connection getConnection(String username, String passwordValue) throws SQLException {
        return DriverManager.getConnection(url, username, passwordValue);
    }
    @Override public PrintWriter getLogWriter() { return DriverManager.getLogWriter(); }
    @Override public void setLogWriter(PrintWriter out) { DriverManager.setLogWriter(out); }
    @Override public void setLoginTimeout(int seconds) { DriverManager.setLoginTimeout(seconds); }
    @Override public int getLoginTimeout() { return DriverManager.getLoginTimeout(); }
    @Override public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("No parent logger");
    }
    @Override public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface != null && iface.isInstance(this)) return iface.cast(this);
        throw new SQLException("Not a wrapper for " + iface);
    }
    @Override public boolean isWrapperFor(Class<?> iface) { return iface != null && iface.isInstance(this); }
}
