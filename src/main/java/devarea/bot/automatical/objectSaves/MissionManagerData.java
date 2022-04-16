package devarea.bot.automatical.objectSaves;

import com.fasterxml.jackson.annotation.JsonProperty;
import devarea.bot.commands.object_for_stock.MessageSeria;
import devarea.bot.commands.object_for_stock.Mission;

import java.util.ArrayList;

public class MissionManagerData {
    @JsonProperty("mission_follow_id")
    public int missionFollowId;
    @JsonProperty("missions")
    public ArrayList<Mission> missions;
    @JsonProperty("missions_follow")
    public ArrayList<MissionFollow> missionsFollow;

    public MissionManagerData(final ArrayList<Mission> missions, final int missionFollowId, final ArrayList<MissionFollow> missionsFollow) {
        this.missionFollowId = missionFollowId;
        this.missions = missions;
        this.missionsFollow = missionsFollow;
    }

    public MissionManagerData() {
    }

    public static class MissionFollow {
        @JsonProperty("mission_id")
        public int missionID;
        @JsonProperty("message")
        public MessageSeria messageSeria;
        @JsonProperty("client_id")
        public String clientID;
        @JsonProperty("dev_id")
        public String devID;

        public MissionFollow(int missionID, final MessageSeria messageSeria, final String clientID, final String devID) {
            this.missionID = missionID;
            this.messageSeria = messageSeria;
            this.clientID = clientID;
            this.devID = devID;
        }

        public MissionFollow() {

        }

    }
}
