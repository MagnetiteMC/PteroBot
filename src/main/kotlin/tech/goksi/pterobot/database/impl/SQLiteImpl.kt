package tech.goksi.pterobot.database.impl

import com.mattmalec.pterodactyl4j.client.entities.Account
import com.mattmalec.pterodactyl4j.exceptions.LoginException
import dev.minn.jda.ktx.util.SLF4J
import net.dv8tion.jda.api.entities.UserSnowflake
import tech.goksi.pterobot.database.DataStorage
import tech.goksi.pterobot.util.Common
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import kotlin.system.exitProcess

/*TODO: refactor databases, LinkedMember object*/
class SQLiteImpl : DataStorage {
    private val connection: Connection
    private val logger by SLF4J

    init {
        Class.forName("org.sqlite.JDBC")
        connection = DriverManager.getConnection("jdbc:sqlite:database.db")
        val statement = connection.createStatement()
        statement.addBatch(
            "create table if not exists Members(id integer not null primary key autoincrement, discordID bigint not null, apiID integer, foreign key(apiID) references Keys(id) on delete set null)"
        )
        statement.addBatch(
            "create table if not exists Keys(id integer not null primary key autoincrement, \"key\" char(48) not null, \"admin\" boolean not null)"
        )
        statement.addBatch(
            "create table if not exists Accounts(id integer not null primary key autoincrement, username varchar(25), memberID integer, foreign key(memberID) references Members(id))"
        )
        statement.use {
            try {
                it.executeBatch()
            } catch (exception: SQLException) {
                logger.error("Failed to initialize SQLite database... exiting", exception)
                exitProcess(1)
            }
        }
    }

    override fun getApiKey(id: Long): String? {
        val statement = connection.prepareStatement(
            "select Keys.key from Keys inner join Members on Keys.id = Members.apiID where Members.discordID = ?"
        )
        statement.setLong(1, id)
        statement.use {
            try {
                val resultSet = it.executeQuery()
                if (resultSet.next()) return resultSet.getString("key")
            } catch (exception: SQLException) {
                logger.error("Failed to get api key for $id", exception)
            }
            return null
        }
    }

    override fun isPteroAdmin(id: Long): Boolean {
        val statement = connection.prepareStatement(
            "select Keys.admin from Keys where id in (" +
                    "select id from Members where discordID = ?)"
        )
        statement.setLong(1, id)
        statement.use {
            try {
                val resultSet = it.executeQuery()
                if (resultSet.next()) return resultSet.getBoolean("isAdmin")
            } catch (exception: SQLException) {
                logger.error("Failed to get admin status of $id", exception)
            }
            return false
        }
    }

    @Throws(LoginException::class, SQLException::class)
    override fun link(snowflake: UserSnowflake, apiKey: String): Account {
        val statement = connection.prepareStatement(
            "INSERT INTO Keys (DiscordID, ApiKey, isAdmin) VALUES (?,?,?)"
        )
        val pteroUser = Common.createClient(apiKey)!!.retrieveAccount().execute() //throws LoginException
        statement.setLong(1, snowflake.idLong)
        statement.setString(2, apiKey)
        statement.setBoolean(3, pteroUser.isRootAdmin)
        statement.use { it.executeUpdate() } //should catch SQLException later in command
        return pteroUser
    }

    override fun unlink(id: Long) {
        val statement = connection.prepareStatement(
            "delete from Keys where id in (" +
                    "select id from Members where discordID = ? )"
        )
        statement.setLong(1, id)
        statement.use {
            try {
                it.executeUpdate()
            } catch (exception: SQLException) {
                logger.error("Failed to delete api key for $id", exception)
            }
        }
    }

    override fun isLinked(id: Long): Boolean {
        val statement = connection.prepareStatement(
            "SELECT apiID FROM Members WHERE discordID = ?"
        )
        statement.setLong(1, id)
        statement.use {
            return try {
                val resultSet = it.executeQuery()
                if(!resultSet.next()) false
                else resultSet.getInt("apiID") != 0
            } catch (exception: SQLException) {
                logger.error("Failed to check linked status for $id", exception)
                false
            }
        }
    }
}