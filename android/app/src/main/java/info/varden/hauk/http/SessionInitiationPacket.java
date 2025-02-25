package info.varden.hauk.http;

import android.content.Context;

import info.varden.hauk.Constants;
import info.varden.hauk.R;
import info.varden.hauk.struct.AdoptabilityPreference;
import info.varden.hauk.struct.Session;
import info.varden.hauk.struct.Share;
import info.varden.hauk.struct.ShareMode;
import info.varden.hauk.struct.Version;
import info.varden.hauk.utils.TimeUtils;

/**
 * Packet sent to initiate a sharing session on the server. Creates a share of a given type.
 *
 * @author Marius Lindvall
 */
public class SessionInitiationPacket extends Packet {
    private final InitParameters params;
    private final ResponseHandler handler;

    /**
     * The sharing mode for the initial share that the session is being created for. Determined by
     * which constructor is called.
     */
    private ShareMode mode;

    private SessionInitiationPacket(Context ctx, InitParameters params, ResponseHandler handler) {
        super(ctx, params.getServerURL(), Constants.URL_PATH_CREATE_SHARE);
        this.params = params;
        this.handler = handler;
        setParameter(Constants.PACKET_PARAM_PASSWORD, params.getPassword());
        setParameter(Constants.PACKET_PARAM_DURATION, String.valueOf(params.getDuration()));
        setParameter(Constants.PACKET_PARAM_INTERVAL, String.valueOf(params.getInterval()));
    }

    /**
     * Creates a packet and designates it as a request to create a single-user share.
     *
     * @param ctx           Android application context.
     * @param params        Session initiation parameters.
     * @param allowAdoption Whether or not to allow this share to be adopted into a group share.
     */
    public SessionInitiationPacket(Context ctx, InitParameters params, ResponseHandler handler, AdoptabilityPreference allowAdoption) {
        this(ctx, params, handler);
        this.mode = ShareMode.CREATE_ALONE;
        setParameter(Constants.PACKET_PARAM_SHARE_MODE, String.valueOf(this.mode.getIndex()));
        setParameter(Constants.PACKET_PARAM_ADOPTABLE, allowAdoption == AdoptabilityPreference.ALLOW_ADOPTION ? "1" : "0");
    }

    /**
     * Creates a packet and designates it as a request to create a group share.
     *
     * @param ctx      Android application context.
     * @param params   Session initiation parameters.
     * @param nickname The nickname to display on the map.
     */
    public SessionInitiationPacket(Context ctx, InitParameters params, ResponseHandler handler, String nickname) {
        this(ctx, params, handler);
        this.mode = ShareMode.CREATE_GROUP;
        setParameter(Constants.PACKET_PARAM_SHARE_MODE, String.valueOf(this.mode.getIndex()));
        setParameter(Constants.PACKET_PARAM_NICKNAME, nickname);
    }

    /**
     * Creates a packet and designates it as a request to join an existing group share.
     *
     * @param ctx      Android application context.
     * @param params   Session initiation parameters.
     * @param nickname The nickname to display on the map.
     * @param groupPin    The PIN code to join the group.
     */
    public SessionInitiationPacket(Context ctx, InitParameters params, ResponseHandler handler, String nickname, String groupPin) {
        this(ctx, params, handler);
        this.mode = ShareMode.JOIN_GROUP;
        setParameter(Constants.PACKET_PARAM_SHARE_MODE, String.valueOf(this.mode.getIndex()));
        setParameter(Constants.PACKET_PARAM_NICKNAME, nickname);
        setParameter(Constants.PACKET_PARAM_GROUP_PIN, groupPin);
    }

    @Override
    protected final void onSuccess(String[] data, Version backendVersion) throws ServerException {
        // Check if the server is out of date for group shares, if applicable.
        if (this.mode.isGroupType()) {
            if (!backendVersion.isAtLeast(Constants.VERSION_COMPAT_GROUP_SHARE)) {
                // If the server is indeed out of date, override the sharing mode to reflect what
                // was actually created on the server.
                this.mode = ShareMode.CREATE_ALONE;
                this.handler.onShareModeIncompatible(this.mode, backendVersion);
            }
        }

        // Somehow the data array can be empty? Check for this.
        if (data.length < 1) {
            throw new ServerException(getContext(), R.string.err_empty);
        }

        // A successful session initiation contains "OK" on line 1, the session ID on line 2, and a
        // publicly sharable tracking link on line 3.
        if (data[0].equals(Constants.PACKET_RESPONSE_OK)) {
            String sessionID = data[1];
            String viewURL = data[2];
            String joinCode = null;
            String viewID = viewURL;

            // If the share is compatible, fetch the group join code.
            if (this.mode == ShareMode.CREATE_GROUP) {
                joinCode = data[3];
            }

            // If the server sends it, get the internal share ID as well for the list of currently
            // active shares in the UI. It is better UX to display this instead of the full URL in
            // the list, but fall back to the full URL if needed.
            if (backendVersion.isAtLeast(Constants.VERSION_COMPAT_VIEW_ID)) {
                viewID = this.mode == ShareMode.CREATE_GROUP ? data[4] : data[3];
            }

            // Create a share and pass it upstream.
            Session session = new Session(this.params.getServerURL(), backendVersion, sessionID, this.params.getDuration() * TimeUtils.MILLIS_PER_SECOND + System.currentTimeMillis(), this.params.getInterval());
            Share share = new Share(session, viewURL, viewID, joinCode, this.mode);

            this.handler.onSessionInitiated(share);
        } else {
            // If the first line of the response is not "OK", an error of some sort has occurred and
            // should be displayed to the user.
            StringBuilder err = new StringBuilder();
            for (String line : data) {
                err.append(line);
                err.append(System.lineSeparator());
            }
            throw new ServerException(err.toString());
        }
    }

    @Override
    protected final void onFailure(Exception ex) {
        this.handler.onFailure(ex);
    }

    /**
     * In order to avoid code duplication, we request a common handler that will be used and whose
     * methods will be called instead of abstract methods directly in the packet class. This allows
     * us to have a single instance of the handler instead of having to reimplement the handlers for
     * all types of shares.
     */
    public interface ResponseHandler extends FailureHandler {
        /**
         * Called when the session is successfully initiated.
         *
         * @param share The share that the session was created for. A {@link Session} can be
         *              extracted using {@code share.getSession()}.
         */
        void onSessionInitiated(Share share);

        /**
         * Called if the share mode was forcibly downgraded because the backend doesn't support the
         * provided share mode.
         *
         * @param downgradeTo    The share mode that is used instead of the requested mode.
         * @param backendVersion The Hauk backend version.
         */
        void onShareModeIncompatible(ShareMode downgradeTo, Version backendVersion);
    }

    /**
     * Contains initialization parameters that are shared for all constructors of this packet. Used
     * to prevent bloated constructors with a large number of duplicate parameters.
     */
    public static final class InitParameters {
        private final String server;
        private final String password;
        private final int duration;
        private final int interval;

        /**
         * Declares initialization parameters for a session initiation request.
         *
         * @param server   The full URL to the server to create a session on.
         * @param password The backend password.
         * @param duration The duration, in seconds, to run the share for.
         * @param interval The interval, in seconds, between each sent location update.
         */
        public InitParameters(String server, String password, int duration, int interval) {
            this.server = server;
            this.password = password;
            this.duration = duration;
            this.interval = interval;
        }

        String getServerURL() {
            return this.server;
        }

        String getPassword() {
            return this.password;
        }

        int getDuration() {
            return this.duration;
        }

        int getInterval() {
            return this.interval;
        }
    }
}
