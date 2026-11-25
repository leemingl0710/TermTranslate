
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnection {

    private static final String URL =
        "jdbc:mysql://localhost:3306/student_learning_db"
        + "?characterEncoding=UTF-8";

    private static final String USER = "root";

    public static Connection getConnection(String password)
            throws SQLException {

        return DriverManager.getConnection(
            URL, USER, password
        );
    }
}

