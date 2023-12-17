package ca.ubc.cs317.dict.net;

import ca.ubc.cs317.dict.model.Database;
import ca.ubc.cs317.dict.model.Definition;
import ca.ubc.cs317.dict.model.MatchingStrategy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.*;

/**
 * Created by Jonatan on 2017-09-09.
 */
public class DictionaryConnection {

    private static final int DEFAULT_PORT = 2628;
    private Socket dictSocket;
    private PrintWriter printWriter;

    private BufferedReader reader;
    /** Establishes a new connection with a DICT server using an explicit host and port number, and handles initial
     * welcome messages.
     *
     * @param host Name of the host where the DICT server is running
     * @param port Port number used by the DICT server
     * @throws DictConnectionException If the host does not exist, the connection can't be established, or the messages
     * don't match their expected value.
     */
    public DictionaryConnection(String host, int port) throws DictConnectionException {
        try  {
            dictSocket = new Socket(host, port);
            reader = new BufferedReader(new InputStreamReader(dictSocket.getInputStream()));
            System.out.println(reader);
            printWriter = new PrintWriter(dictSocket.getOutputStream(), true);
            if (Status.readStatus(reader).isNegativeReply()){
                throw new DictConnectionException("Transient or Permanent Failure");
            }
            System.out.println("connected");
        } catch (IOException e) {
            throw new DictConnectionException("An error occurred while creating the socket.", e);
        }
    }

    /** Establishes a new connection with a DICT server using an explicit host, with the default DICT port number, and
     * handles initial welcome messages.
     *
     * @param host Name of the host where the DICT server is running
     * @throws DictConnectionException If the host does not exist, the connection can't be established, or the messages
     * don't match their expected value.
     */
    public DictionaryConnection(String host) throws DictConnectionException {
        this(host, DEFAULT_PORT);
    }

    /** Sends the final QUIT message and closes the connection with the server. This function ignores any exception that
     * may happen while sending the message, receiving its reply, or closing the connection.
     *
     */
    public synchronized void close() {
        try {
            printWriter.println("QUIT");
            dictSocket.close();
        } catch (IOException e){
            e.printStackTrace();
        }
    }

    /** Requests and retrieves all definitions for a specific word.
     *
     * @param word The word whose definition is to be retrieved.
     * @param database The database to be used to retrieve the definition. A special database may be specified,
     *                 indicating either that all regular databases should be used (database name '*'), or that only
     *                 definitions in the first database that has a definition for the word should be used
     *                 (database '!').
     * @return A collection of Definition objects containing all definitions returned by the server.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected value.
     */
    public synchronized Collection<Definition> getDefinitions(String word, Database database) throws DictConnectionException {
        Collection<Definition> set = new ArrayList<>();

        printWriter.println("DEFINE" + " " + database.getName() + " " + word);
        Status status = Status.readStatus(reader);
        switch(status.getStatusCode()) {
            case 550:
            case 552:
            case 551:
                return set;
            case 150:
                // gets the number of databases with definitions (1 db can have multiple definitions)
                int numDefinitions = Integer.parseInt(DictStringParser.splitAtoms(status.getDetails())[0]);
                for (int i = 0; i < numDefinitions; i++){
                    String line;
                    try {
                        line = reader.readLine();
                    } catch (IOException e) {
                        throw new DictConnectionException();
                    }
                    String[] strings = DictStringParser.splitAtoms(line);
                    Definition definition = new Definition(word, strings[2]);
                    while (true) {
                        try {
                            line = reader.readLine();
                            if (line.equals(".")){
                                break;
                            }
                            definition.appendDefinition(line);
                        } catch (IOException e) {
                            throw new DictConnectionException();
                        }
                    }
                    set.add(definition);
                }

        }
        return set;
    }

    /** Requests and retrieves a list of matches for a specific word pattern.
     *
     * @param word     The word whose definition is to be retrieved.
     * @param strategy The strategy to be used to retrieve the list of matches (e.g., prefix, exact).
     * @param database The database to be used to retrieve the definition. A special database may be specified,
     *                 indicating either that all regular databases should be used (database name '*'), or that only
     *                 matches in the first database that has a match for the word should be used (database '!').
     * @return A set of word matches returned by the server.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected value.
     */
    public synchronized Set<String> getMatchList(String word, MatchingStrategy strategy, Database database) throws DictConnectionException {
        Set<String> set = new LinkedHashSet<>();

        printWriter.println("MATCH" + " " +database.getName() + " " + strategy.getName() + " " + word);
        Status status = Status.readStatus(reader);

        switch(status.getStatusCode()) {
            case 550:
            case 552:
            case 551:
                return set;
            case 152:
                try {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.equals("250 ok")) {
                            break; // Exit the loop when "250 ok" is encountered.
                        }
                        String[] strings = DictStringParser.splitAtoms(line);
                        if (strings.length == 2) {
                            set.add(strings[1]);
                        }
                    }
                } catch (IOException e) {
                    throw new DictConnectionException();
                }
        }
        return set;
    }

    /** Requests and retrieves a map of database name to an equivalent database object for all valid databases used in the server.
     *
     * @return A map of Database objects supported by the server.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected value.
     */
    public synchronized Map<String, Database> getDatabaseList() throws DictConnectionException {
        Map<String, Database> databaseMap = new HashMap<>();
        printWriter.println("SHOW DB");
        Status status =  Status.readStatus(reader);
        if (!status.isNegativeReply()) {
            // if nothing found
            if (status.getStatusCode() == 554){
                return databaseMap;
            }
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.equals("250 ok")) {
                        break; // Exit the loop when "250 ok" is encountered.
                    }
                    String[] strings = DictStringParser.splitAtoms(line);
                    if (strings.length == 2) {
                        Database db = new Database(strings[0], strings[1]);
                        databaseMap.put(strings[0], db);
                    }
                }
            } catch (IOException e) {
                throw new DictConnectionException();
            }
        } else {
            return databaseMap;
        }
        return databaseMap;
    }

    /** Requests and retrieves a list of all valid matching strategies supported by the server.
     *
     * @return A set of MatchingStrategy objects supported by the server.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected value.
     */
    public synchronized Set<MatchingStrategy> getStrategyList() throws DictConnectionException {
        Set<MatchingStrategy> set = new LinkedHashSet<>();
        printWriter.println("SHOW STRAT");
        Status status =  Status.readStatus(reader);
        if (!status.isNegativeReply()) {
            // if nothing found
            if (status.getStatusCode() == 555){
                return set;
            }
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.equals("250 ok")) {
                        break; // Exit the loop when "250 ok" is encountered.
                    }
                    String[] strings = DictStringParser.splitAtoms(line);
                    if (strings.length == 2) {
                        MatchingStrategy matchingStrategy = new MatchingStrategy(strings[0], strings[1]);
                        set.add(matchingStrategy);
                        System.out.println(line);
                    }
                }
            } catch (IOException e) {
                throw new DictConnectionException();
            }
        } else {
            return set;
        }
        return set;
    }

    /** Requests and retrieves detailed information about the currently selected database.
     *
     * @return A string containing the information returned by the server in response to a "SHOW INFO <db>" command.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected value.
     */
    public synchronized String getDatabaseInfo(Database d) throws DictConnectionException {
	StringBuilder sb = new StringBuilder();

        // TODO Add your code here

        return sb.toString();
    }
}
