package devarea.bot.automatical;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import devarea.Main;
import devarea.bot.cache.MemberCache;
import devarea.bot.Init;
import devarea.bot.automatical.handlerData.MissionManagerData;
import devarea.bot.commands.Command;
import devarea.bot.commands.commandTools.MessageSeria;
import devarea.bot.commands.commandTools.Mission;
import devarea.bot.presets.ColorsUsed;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.object.PermissionOverwrite;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.TextChannel;
import discord4j.core.object.reaction.ReactionEmoji;
import discord4j.core.spec.*;
import discord4j.rest.util.Permission;
import discord4j.rest.util.PermissionSet;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import static devarea.bot.commands.Command.delete;
import static devarea.bot.commands.Command.send;
import static devarea.bot.event.FunctionEvent.startAway;

public class MissionsHandler {

    public static Message messsage;
    private static ArrayList<Mission> missions = new ArrayList<>();
    private static ArrayList<MissionManagerData.MissionFollow> missionsFollow = new ArrayList<>();
    private static TextChannel channel;
    private static int missionFollowId = 0;

    public static void init() {
        load();
        channel = (TextChannel) Init.devarea.getChannelById(Init.idMissionsPayantes).block();
        Message msg = channel.getLastMessage().block();
        if (msg.getEmbeds().size() == 0 || msg.getEmbeds().get(0).getTitle().equals("Créer une mission."))
            sendLastMessage();
        else messsage = msg;
        new Thread(() -> {
            try {
                while (true) {
                    if (verif()) save();
                    if (!Main.developing)
                        checkForUpdate();
                    Thread.sleep(3600000);
                }
            } catch (InterruptedException e) {
            }
        }).start();
    }

    public static void update() {
        delete(false, messsage);
        sendLastMessage();
    }

    private static void sendLastMessage() {
        messsage = Command.send(channel, MessageCreateSpec.builder()
                        .addEmbed(EmbedCreateSpec.builder().color(ColorsUsed.same)
                                .title("Créer une mission.")
                                .description("Cliquez sur le bouton ci-dessous pour créer une mission !").build())
                        .addComponent(ActionRow.of(Button.link("https://devarea.fr/mission-creator", "devarea.fr"))).build(),
                true);
    }

    public static boolean interact(final ButtonInteractionEvent event) {
        /*if (event.getCustomId().equals("createMission")) {
            CommandManager.addManualCommand(event.getInteraction().getMember().get(),
                    new ConsumableCommand(CreateMission.class) {
                @Override
                protected Command command() {
                    return new CreateMission(this.member);
                }
            });
            return true;
        } else*/
        if (event.getCustomId().startsWith("mission")) {
            return actionToUpdateMessage(event);
        } else if (event.getCustomId().equals("took_mission")) {
            return actionToTookMission(event);
        } else if (event.getCustomId().equals("followMission_close")) {
            return actionToCloseFollowedMission(event);
        }

        return false;
    }

    public static void add(Mission mission) {
        missions.add(mission);
        save();
    }

    public static void remove(Mission mission) {
        missions.remove(mission);
        save();
    }

    public static void clearThisMission(Mission mission) {
        missions.remove(mission);
        startAway(() -> {
            try {
                delete(true, mission.getMessage().getMessage());
            } catch (Exception e) {

            }
        });
        save();
    }

    private static void load() {
        ObjectMapper mapper = new ObjectMapper();
        File file = new File("./mission.json");
        if (!file.exists()) save();
        try {
            MissionManagerData missionData = mapper.readValue(file, new TypeReference<>() {
            });
            missions = missionData.missions;
            missionFollowId = missionData.missionFollowId;
            missionsFollow = missionData.missionsFollow;
            System.out.println("Missions loaded : " + missions.size() + " detected !");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static boolean verif() {
        ArrayList<Mission> atRemove = new ArrayList<>();
        TextChannel channel = (TextChannel) Init.devarea.getChannelById(Init.idMeetupVerif).block();
        for (Mission mission : missions) {
            if (!MemberCache.contain(mission.getMemberId())) {
                atRemove.add(mission);
                startAway(() -> {
                    try {
                        delete(false, mission.getMessage().getMessage());
                    } catch (Exception e) {
                    }
                });
            }
        }

        System.out.println("Il y a en tout : " + atRemove.size() + " missions à supprimer !");

        if (atRemove.size() == 0) return false;

        missions.removeAll(atRemove);
        return true;
    }

    public static void save() {
        ObjectMapper mapper = new ObjectMapper();
        try {
            MissionManagerData missionData = new MissionManagerData(missions, missionFollowId, missionsFollow);
            mapper.writeValue(new File("./mission.json"), missionData);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static ArrayList<Mission> getOf(Snowflake id) {
        ArrayList<Mission> newArray = new ArrayList<>();
        for (int i = 0; i < missions.size(); i++) {
            if (missions.get(i).getMemberId().equals(id.asString())) newArray.add(missions.get(i));
        }
        return newArray;
    }

    /*
    Do not use this method
     */
    public static ArrayList<Mission> getMissions() {
        return missions;
    }

    public static void checkForUpdate() {
        ArrayList<Mission> spoiled_missions = new ArrayList<>();

        for (Mission mission : missions)
            if (mission.getMessage_verification() == null && System.currentTimeMillis() - mission.getLast_update() > 604800000)
                askValidate(mission);
            else if (mission.getMessage_verification() != null && System.currentTimeMillis() - mission.getLast_update() > 864000000)
                spoiled_missions.add(mission);

        for (Mission mission : spoiled_missions)
            validateSpoilAction(mission);

    }

    public static boolean actionToUpdateMessage(final ButtonInteractionEvent event) {
        Mission current_mission = null;
        for (Mission mission : missions)
            if (mission.getMessage_verification() != null && mission.getMessage_verification().getMessageID().equals(event.getMessageId()))
                current_mission = mission;

        if (current_mission != null) {
            if (event.getCustomId().equals("mission_yes")) {
                event.getInteraction().getChannel().block().getMessageById(current_mission.getMessage_verification().getMessageID()).block().edit(MessageEditSpec.builder().addEmbed(EmbedCreateSpec.builder()
                                .title("Mission actualisé !")
                                .description("La mission : **" + current_mission.getTitle() + "**, a été défini comme" +
                                        " valide pour encore 7 jours.\n\nVous recevrez une nouvelle demande de " +
                                        "validation dans 7 jours.")
                                .color(ColorsUsed.just).build())
                        .components(new ArrayList<>())
                        .build()).block();
                current_mission.update();
                current_mission.setMessage_verification(null);
                System.out.println(event.getInteraction().getUser().getUsername() + " a prolongé la validité de sa " +
                        "mission de 7 jours.");
                save();
            } else if (event.getCustomId().equals("mission_no")) {
                event.getInteraction().getChannel().block().getMessageById(current_mission.getMessage_verification().getMessageID()).block().edit(MessageEditSpec.builder().addEmbed(EmbedCreateSpec.builder()
                                .title("Mission supprimé !")
                                .description("La mission : **" + current_mission.getTitle() + "**, a été " +
                                        "définitivement supprimé !")
                                .color(ColorsUsed.just).build())
                        .components(new ArrayList<>())
                        .build()).block();
                clearThisMission(current_mission);
                System.out.println(event.getInteraction().getUser().getUsername() + " a supprimé sa mission !");
            }
            return true;
        }
        return false;
    }

    public static void askValidate(Mission mission) {
        Member mission_member = MemberCache.get(mission.getMemberId());
        System.out.println("Une vérification de validité de mission a été envoyé à : " + mission_member.getDisplayName());
        Message message = mission_member.getPrivateChannel().block().createMessage(MessageCreateSpec.builder()
                .addEmbed(EmbedCreateSpec.builder()
                        .title("Vérification de la validité d'une mission.")
                        .description("Vous avez une mission actuellement active !\n\nLe titre de cette mission est : " +
                                "**" + mission.getTitle() + "**\n\nIl vous reste 3 jours pour nous comfirmer ou non " +
                                "si cette mission est-elle toujours d'actualité ?\n\nSi oui : <:ayy:" + Init.idYes.getId().asString() + "> si non : <:ayy:" + Init.idNo.getId().asString() + ">.")
                        .color(ColorsUsed.same).build())
                .addComponent(ActionRow.of(Button.primary("mission_yes", ReactionEmoji.custom(Init.idYes)),
                        Button.primary("mission_no", ReactionEmoji.custom(Init.idNo))))
                .build()).block();
        mission.setLast_update(System.currentTimeMillis() - 604800000);
        mission.setMessage_verification(new MessageSeria(message));
        save();
    }

    public static void validateSpoilAction(Mission mission) {
        Member mission_member = MemberCache.get(mission.getMemberId());
        System.out.println("Une vérification de validité de mission a expirée pour le membre : " + mission_member.getDisplayName());
        mission_member.getPrivateChannel().block().getMessageById(mission.getMessage_verification().getMessageID()).block()
                .edit(MessageEditSpec.builder()
                        .addEmbed(EmbedCreateSpec.builder()
                                .title("Mission supprimé !")
                                .description("Le délais des 3 jours a été expiré. La mission : **" + mission.getTitle() + "**, a été définitivement supprimé !")
                                .color(ColorsUsed.wrong).build())
                        .components(new ArrayList<>()).build())
                .subscribe();
        clearThisMission(mission);
    }


    public static boolean actionToTookMission(final ButtonInteractionEvent event) {
        Mission mission = getMissionOfMessage(event.getMessageId());
        Snowflake member_react_id = event.getInteraction().getMember().get().getId();
        if (mission != null) {
            if (mission.getMemberId().equals(member_react_id.asString())) {
                event.reply(InteractionApplicationCommandCallbackSpec.builder()
                        .ephemeral(true)
                        .addEmbed(EmbedCreateSpec.builder()
                                .title("Error !")
                                .description("Vous ne pouvez pas prendre votre propre mission !")
                                .color(ColorsUsed.wrong)
                                .build())
                        .build()).subscribe();
                return true;
            }
            if (alreadyHaveAChannel(mission.getMemberId(), member_react_id.asString())) {
                event.reply(InteractionApplicationCommandCallbackSpec.builder()
                        .ephemeral(true)
                        .addEmbed(EmbedCreateSpec.builder()
                                .title("Error !")
                                .description("Vous suivez déjà cette mission !")
                                .color(ColorsUsed.wrong)
                                .build())
                        .build()).subscribe();
                return true;
            }
            Set<PermissionOverwrite> set = new HashSet<>();
            set.add(PermissionOverwrite.forRole(Init.idRoleRulesAccepted, PermissionSet.of(),
                    PermissionSet.of(Permission.VIEW_CHANNEL)));
            set.add(PermissionOverwrite.forRole(Init.devarea.getEveryoneRole().block().getId(), PermissionSet.of(),
                    PermissionSet.of(Permission.VIEW_CHANNEL)));
            set.add(PermissionOverwrite.forMember(member_react_id, PermissionSet.of(Permission.VIEW_CHANNEL,
                    Permission.READ_MESSAGE_HISTORY, Permission.SEND_MESSAGES), PermissionSet.of()));
            set.add(PermissionOverwrite.forMember(Snowflake.of(mission.getMemberId()),
                    PermissionSet.of(Permission.VIEW_CHANNEL, Permission.READ_MESSAGE_HISTORY,
                            Permission.SEND_MESSAGES), PermissionSet.of()));
            set.add(PermissionOverwrite.forRole(Snowflake.of("868441850971824149"),
                    PermissionSet.of(Permission.VIEW_CHANNEL), PermissionSet.of()));

            TextChannel channel = Init.devarea.createTextChannel(TextChannelCreateSpec.builder()
                    .parentId(Snowflake.of("964757205184299028"))
                    .name("Suivis de mission n°" + ++missionFollowId)
                    .permissionOverwrites(set)
                    .build()).block();
            int id = missionFollowId;

            send(channel, MessageCreateSpec.builder()
                    .addEmbed(EmbedCreateSpec.builder()
                            .title(mission.getTitle())
                            .description(mission.getDescriptionText() + "\n\nPrix: " + mission.getPrix() + "\nDate de" +
                                    " retour: " + mission.getDateRetour() + "\nType de support: " + mission.getSupport() + "\nLangage: " + mission.getLangage() + "\nNiveau estimé: " + mission.getNiveau())
                            .color(ColorsUsed.just)
                            .build())
                    .build(), false);

            Message message = channel.createMessage(MessageCreateSpec.builder()
                    .content("<@" + member_react_id.asString() + "> -> <@" + mission.getMemberId() + ">")
                    .addEmbed(EmbedCreateSpec.builder()
                            .title("Suivis de Mission !")
                            .description("Bienvenue dans ce channel !\n\n" +
                                    "Ce channel a été créé car <@" + member_react_id.asString() + "> est intéressé " +
                                    "par la mission de <@" + mission.getMemberId() + ">." +
                                    "\n\nCe channel est dédié pour vous, ainsi qu'à la mise en place de la mission et" +
                                    " nous vous demandons de passer exclusivement par ce channel pour toute " +
                                    "discussion à propos de celle-ci." +
                                    "\n\nCeci a pour but d'augmenter la fiabilité des clients et des développeurs " +
                                    "pour qu'une mission puisse se passer de la meilleure des manières" +
                                    ".\nRèglementation des missions : <#768435208906735656>." +
                                    "\n\nVous pouvez clôturer ce channel à tout moment !")
                            .color(ColorsUsed.same)
                            .build())
                    .addComponent(ActionRow.of(Button.secondary("followMission_close", "Cloturer le channel")))
                    .build()).block();

            missionsFollow.add(new MissionManagerData.MissionFollow(id, new MessageSeria(message),
                    mission.getMemberId(), member_react_id.asString()));

            save();
            return true;
        }
        return false;
    }

    public static Mission getMissionOfMessage(final Snowflake message_id) {
        for (Mission mission : missions)
            if (mission.getMessage().getMessageID().equals(message_id))
                return mission;
        return null;
    }

    public static boolean alreadyHaveAChannel(final String clientID, final String devID) {
        for (MissionManagerData.MissionFollow mission : missionsFollow)
            if (mission.clientID.equals(clientID) && mission.devID.equals(devID))
                return true;
        return false;
    }

    public static boolean actionToCloseFollowedMission(final ButtonInteractionEvent event) {
        final MissionManagerData.MissionFollow mission = getByMessageID(event.getMessageId());
        if (mission != null) {

            send(((TextChannel) event.getInteraction().getChannel().block()), MessageCreateSpec.builder()
                    .addEmbed(EmbedCreateSpec.builder()
                            .title("Clôture du Suivis de mission.")
                            .description("La clôture du suivis a été éxécuté par : <@" + event.getInteraction().getMember().get().getId().asString() + ">. Le suivis fermera dans 1 heure.")
                            .color(ColorsUsed.same)
                            .timestamp(Instant.now())
                            .build())
                    .build(), false);

            mission.messageSeria.getMessage().edit(MessageEditSpec.builder()
                    .components(new ArrayList<>())
                    .build()).subscribe();

            startAway(() -> {

                try {
                    Thread.sleep(3600000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    missionsFollow.remove(mission);

                    Set<PermissionOverwrite> set = new HashSet<>();
                    set.add(PermissionOverwrite.forRole(Init.idRoleRulesAccepted, PermissionSet.of(),
                            PermissionSet.of(Permission.VIEW_CHANNEL)));
                    set.add(PermissionOverwrite.forRole(Init.devarea.getEveryoneRole().block().getId(),
                            PermissionSet.of(), PermissionSet.of(Permission.VIEW_CHANNEL)));
                    set.add(PermissionOverwrite.forMember(Snowflake.of(mission.clientID), PermissionSet.of(),
                            PermissionSet.of(Permission.VIEW_CHANNEL, Permission.READ_MESSAGE_HISTORY,
                                    Permission.SEND_MESSAGES)));
                    set.add(PermissionOverwrite.forMember(Snowflake.of(mission.devID), PermissionSet.of(),
                            PermissionSet.of(Permission.VIEW_CHANNEL, Permission.READ_MESSAGE_HISTORY,
                                    Permission.SEND_MESSAGES)));
                    set.add(PermissionOverwrite.forRole(Snowflake.of("868441850971824149"),
                            PermissionSet.of(Permission.VIEW_CHANNEL), PermissionSet.of()));

                    ((TextChannel) event.getInteraction().getChannel().block()).edit(TextChannelEditSpec.builder()
                            .name("Closed n°" + mission.missionID)
                            .permissionOverwrites(set)
                            .build()).subscribe();
                    EmbedCreateSpec embed = EmbedCreateSpec.builder()
                            .title("Clôture du suivis n°" + mission.missionID + " !")
                            .description("Le suivis de mission n°" + mission.missionID + " a été clôturé à la demande" +
                                    " de <@" + event.getInteraction().getMember().get().getId().asString() + ">.")
                            .color(ColorsUsed.just)
                            .build();
                    startAway(() -> MemberCache.get(mission.clientID).getPrivateChannel().block().createMessage(embed).subscribe());
                    startAway(() -> MemberCache.get(mission.devID).getPrivateChannel().block().createMessage(embed).subscribe());

                    save();
                }
            });
        }
        return false;
    }

    public static MissionManagerData.MissionFollow getByMessageID(final Snowflake messageID) {
        for (MissionManagerData.MissionFollow mission : missionsFollow)
            if (mission.messageSeria.getMessageID().equals(messageID))
                return mission;
        return null;
    }

    public static Mission createMission(final String title, final String description, final String prix,
                                        final String dateRetour, final String langage, final String support,
                                        final String niveau, final Member member) {
        Mission createdMission = new Mission(title, description, prix, dateRetour, langage, support, niveau,
                member.getId().asString(), new MessageSeria(
                Objects.requireNonNull(send((TextChannel) Init.devarea.getChannelById(Init.idMissionsPayantes).block(), MessageCreateSpec.builder()
                        .content("**Mission proposée par <@" + member.getId().asString() + "> :**")
                        .addEmbed(EmbedCreateSpec.builder()
                                .title(title)
                                .description(description + "\n\nPrix: " + prix + "\nDate de retour: " + dateRetour +
                                        "\nType de support: " + support + "\nLangage: " + langage + "\nNiveau estimé:" +
                                        " " + niveau + "\n\nCette mission est posté par : " + "<@" + member.getId().asString() + ">.")
                                .color(ColorsUsed.just)
                                .author(member.getDisplayName(), member.getAvatarUrl(), member.getAvatarUrl())
                                .timestamp(Instant.now())
                                .build())
                        .addComponent(ActionRow.of(Button.secondary("took_mission", "Prendre la mission")))
                        .build(), true))
        ));
        MissionsHandler.add(createdMission);
        MissionsHandler.update();
        return createdMission;
    }
}