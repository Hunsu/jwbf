package net.sourceforge.jwbf.mediawiki.actions.queries;

import java.io.IOException;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Ints;

import net.sourceforge.jwbf.core.actions.RequestBuilder;
import net.sourceforge.jwbf.core.actions.util.ActionException;
import net.sourceforge.jwbf.core.actions.util.HttpAction;
import net.sourceforge.jwbf.mapper.JsonMapper;
import net.sourceforge.jwbf.mediawiki.ApiRequestBuilder;
import net.sourceforge.jwbf.mediawiki.MediaWiki;
import net.sourceforge.jwbf.mediawiki.bots.MediaWikiBot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A query class to get a list of pages on the current user's watchlist that were changed within the
 * given time period.
 *
 * You must be logged in or pass a user name and his watchlist token to use this class.
 *
 * @author Rabah Meradi.
 *
 */
public final class WatchList extends BaseQuery<WatchResponse> {

  /*
   * The properties that could be returned with WatchList request.
   */
  public enum WatchListProperties {
    /* The user who made the change */
    USER("user"),
    /* The title of page */
    TITLE("title"),
    /* The edit/log comment */
    COMMENT("comment"),
    /* The edit/log comment in HTML format */
    PARSED_COMMENT("parsedcomment"),
    /* The time and date of the change */
    TIMESTAMP("timestamp"),
    /* Adds timestamp of when the user was last notified about the edit */
    NOTIFICATION_TIMESTAMP("notificationtimestamp"),
    /* The page ID and revision ID */
    IDS("ids"),
    /* The page size before and after the change */
    SIZES("sizes"),
    /* Whether the change is patrolled. */
    PATROL("patrol"),
    /* The flags associated with the change (bot edit, minor edit ...) */
    FLAGS("flags");

    /* The name of the property like it's used by MW */
    private String name;

    WatchListProperties(final String name) {
      this.name = name;
    }

    @Override
    public String toString() {
      return name;
    }
  }

  public enum EditType {
    /* Regular page edits */
    EDIT("edit"),
    /* External edits */
    EXTERNAL("external"),
    /* Pages creation */
    NEW("new"),
    /* Log entries */
    LOG("log");
    /* The name of the property like it's used by MW */
    private String name;

    EditType(final String name) {
      this.name = name;
    }

    @Override
    public String toString() {
      return name;
    }
  }

  public enum Direction {
    /* Older edits first */
    OLDER("older"),
    /* Newer edits first */
    NEWER("newer");
    /* the name used by MW */
    private String name;

    Direction(final String name) {
      this.name = name;
    }

    @Override
    public String toString() {
      return name;
    }
  }

  private static final Logger log = LoggerFactory.getLogger(WatchList.class);

  private final JsonMapper mapper = new JsonMapper();

  private final int limit;
  private final Date start;
  private final Date end;
  private final Direction dir;
  private final ImmutableList<Integer> namespaces;
  private final ImmutableList<WatchListProperties> properties;
  private final String user;
  private final String excludeUser;
  private final String owner;
  private final String token;
  private final ImmutableList<EditType> editType;
  private final boolean showBots;
  private final boolean showAnonymous;
  private final boolean showMinor;

  private WatchListResults responseList;

  private WatchList(final Builder builder) {
    super(builder.bot);
    if (!bot().isLoggedIn()) {
      if (builder.owner == null || builder.token == null) {
        throw new ActionException("Please login first or set owner and token");
      }
    }
    this.limit = builder.limit;
    this.start = builder.start;
    this.end = builder.end;
    this.dir = builder.dir;
    this.namespaces = builder.namespaces;
    this.properties = builder.properties;
    this.user = builder.user;
    this.excludeUser = builder.excludeUser;
    this.owner = builder.owner;
    this.token = builder.token;
    this.editType = builder.editTypes;
    this.showBots = builder.showBots;
    this.showAnonymous = builder.showAnonymous;
    this.showMinor = builder.showMinor;
  }

  // TODO: do we need to check if it's logged?
  private WatchList(final MediaWikiBot bot, final int limit, final Date start, final Date end, final Direction dir,
      final ImmutableList<Integer> namespaces, final ImmutableList<WatchListProperties> properties, final String user,
      final String excludeUser, final String owner, final String token, final ImmutableList<EditType> editTypes,
      final boolean showBots, final boolean showAnonymous, final boolean showMinor) {
    super(bot);
    this.limit = limit;
    this.namespaces = namespaces;
    this.start = start;
    this.end = end;
    this.dir = dir;
    this.properties = properties;
    this.user = user;
    this.excludeUser = excludeUser;
    this.owner = owner;
    this.token = token;
    this.editType = editTypes;
    this.showBots = showBots;
    this.showAnonymous = showAnonymous;
    this.showMinor = showMinor;
  }

  @Override
  protected WatchList copy() {
    return new WatchList(bot(), limit, start, end, dir, namespaces, properties, user, excludeUser,
        owner, token, editType, showBots, showAnonymous, showMinor);
  }

  @Override
  protected HttpAction prepareNextRequest() {
    RequestBuilder requestBuilder = new ApiRequestBuilder().action("query") //
        .formatJson() //
        .param("continue", MediaWiki.urlEncode("-||")) //
        .param("list", "watchlist");
    requestBuilder.param("wlnamespace", MediaWiki.urlEncodedNamespace(namespaces));
    if (!properties.isEmpty()) {
      requestBuilder
          .param("wlprop", MediaWiki.joinParam(new HashSet<WatchListProperties>(properties)));
    }
    if (start != null) {
      requestBuilder.param("wlstart", start);
    }
    if (end != null) {
      requestBuilder.param("wlend", end);
    }
    if (dir != null) {
      requestBuilder.param("wldir", dir.name());
    }
    if (user != null) {
      requestBuilder.param("wluser", user);
    } else {
      if (excludeUser != null) {
        requestBuilder.param("wlexcludeuser", excludeUser);
      }
    }
    if (owner != null) {
      requestBuilder.param("wlowner", owner);
      requestBuilder.param("wltoken", token);
    }
    if (!editType.isEmpty()) {
      requestBuilder.param("wltype", MediaWiki.joinParam(new HashSet<EditType>(editType)));
      Set<String> shows = new HashSet<String>();
      if (showBots) {
        shows.add("bot");
      } else {
        shows.add("!bot");
      }
      if (showAnonymous) {
        shows.add("anon");
      } else {
        shows.add("!anon");
      }
      if (showMinor) {
        shows.add("minor");
      } else {
        shows.add("!minor");
      }
      requestBuilder.param("wlshow", MediaWiki.joinParam(shows));
    }
    if (limit < 1) {
      requestBuilder.param("wllimit", "max");
    } else {
      requestBuilder.param("wllimit", limit);
    }
    if (hasNextPageInfo()) {
      requestBuilder.param("wlcontinue", getNextPageInfo());
    }
    log.debug("using query {}", requestBuilder.build());
    return requestBuilder.buildGet();

  }

  @Override
  protected ImmutableList<WatchResponse> parseElements(final String json) {
    this.responseList = mapper.get(json, WatchListResults.class);
    return ImmutableList.copyOf(responseList.getResults());
  }

  @Override
  protected Optional<String> parseHasMore(final String s) {
    if (responseList.canContinue()) {
      return Optional.of(responseList.getContinueToken());
    } else {
      return Optional.absent();
    }
  }

  public static Builder from(final MediaWikiBot bot) {
    return new Builder(bot);
  }

  public static class Builder {

    private MediaWikiBot bot;
    private int limit = -1;
    private ImmutableList<WatchListProperties> properties =
        new ImmutableList.Builder<WatchListProperties>().build();
    private boolean showBots = true;
    private boolean showAnonymous = true;
    private boolean showMinor = true;
    private ImmutableList<Integer> namespaces = ImmutableList.copyOf(MediaWiki.NS_EVERY);
    private ImmutableList<EditType> editTypes = new ImmutableList.Builder<EditType>().build();
    ;
    private String user = null;
    private String excludeUser = null;
    private Date start = null;
    private Date end = null;
    private Direction dir = null;
    private String owner = null;
    private String token = null;

    public Builder(final MediaWikiBot bot) {
      this.bot = bot;
    }

    /**
     * How many results to return per request. Use a negative value to return the maximum.
     */
    public Builder withLimit(final int limit) {
      this.limit = limit;
      return this;
    }

    /**
     * Only list pages in these namespaces
     */
    public Builder withNamespaces(final int... namespaces) {
      this.namespaces = ImmutableList.copyOf(Ints.asList(namespaces));
      return this;
    }

    /**
     * Which properties to get.
     */
    public Builder withProperties(final WatchListProperties... properties) {
      this.properties = ImmutableList.copyOf(properties);
      return this;
    }

    /**
     * Whatever to include edits made by bots or not
     *
     * @param show include edits made by bots or not
     * TODO @Hunsu never used?
     */
    public Builder showBots(final boolean show) {
      this.showBots = show;
      return this;
    }

    /**
     * Whatever to include edits made by anonymous users or not
     *
     * @param show include edits made by anonymous users or not
     * TODO @Hunsu never used?
     */
    public Builder showAnonymous(final boolean show) {
      this.showAnonymous = show;
      return this;
    }

    /**
     * Include or not edits that are marked as minor
     *
     * @param show include minor edits or not
     * TODO @Hunsu never used?
     */
    public Builder showMinor(final boolean show) {
      this.showMinor = show;
      return this;
    }

    /**
     * Only list certain types of changes
     *
     * @param types the types of changes that will be listed
     * TODO @Hunsu never used?
     */
    public Builder onlyTypes(final EditType... types) {
      this.editTypes = ImmutableList.copyOf(types);
      return this;
    }

    /**
     * Only list changes made by this a specific user
     *
     * @param user  the user
     * TODO @Hunsu never used?
     */
    public Builder onlyUser(final String user) {
      this.user = user;
      return this;
    }

    /**
     * Exclude changes made by a certain user
     *
     * @param user the user
     * TODO @Hunsu never used?
     */
    public Builder excludeUser(final String user) {
      this.excludeUser = user;
      return this;
    }

    /**
     * The user whose watchlist you want
     *
     * @param ownerUser the user
     * @param token the watchlist token of the user
     */
    public Builder owner(final String ownerUser, final String token) {
      if (owner != null && token == null) {
        throw new IllegalArgumentException("owner and token mustn't be null");
      }
      this.owner = ownerUser;
      this.token = token;
      return this;
    }

    /**
     * The timestamp to start listing from
     *
     * @param start the timestamp
     */
    public Builder withStart(final Date start) {
      if (end != null && start != null && end.compareTo(start) < 0) {
        throw new IllegalArgumentException("start must be before end");
      }
      this.start = start;
      return this;
    }

    /**
     * The timestamp to end listing at
     *
     * @param end the timestamp
     * TODO @Hunsu never used?
     */
    public Builder withEnd(final Date end) {
      if (start != null && end != null && end.compareTo(start) < 0) {
        throw new IllegalArgumentException("end must be later than start");
      }
      this.end = end;
      return this;
    }

    /**
     * Direction to list in
     *
     * @param dir the direction
     * TODO @Hunsu never used?
     */
    public Builder withDir(final Direction dir) {
      this.dir = dir;
      return this;
    }

    /**
     * Create a WatchList query with the given configuration
     *
     * @return the watchlist query
     */
    public WatchList build() {
      return new WatchList(this);
    }
  }
}
