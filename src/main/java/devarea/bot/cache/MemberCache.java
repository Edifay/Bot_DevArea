package devarea.bot.cache;

import devarea.bot.cache.tools.CachedMember;
import discord4j.core.object.entity.Member;

import java.util.HashMap;

public class MemberCache {

    private static final HashMap<String, CachedMember> members = new HashMap<>();

    public static Member get(final String memberID) {
        CachedMember cachedMember = getCachedMember(memberID);
        if (cachedMember == null) {
            cachedMember = new CachedMember(memberID);
            members.put(memberID, cachedMember);
        }

        return cachedMember.get();
    }

    public static Member fetch(final String memberID) {
        CachedMember cachedMember = getCachedMember(memberID);
        if (cachedMember == null) {
            cachedMember = new CachedMember(memberID);
            members.put(memberID, cachedMember);
        }

        return cachedMember.fetch();
    }

    public static Member watch(final String memberID) {
        CachedMember cachedMember = getCachedMember(memberID);
        if (cachedMember == null) {
            cachedMember = new CachedMember(memberID);
            cachedMember.get();
            members.put(memberID, cachedMember);
        }
        return cachedMember.watch();
    }

    public static void reset(final String memberID) {
        CachedMember cachedMember = getCachedMember(memberID);
        if (cachedMember == null) {
            cachedMember = new CachedMember(memberID);
            members.put(memberID, cachedMember);
        }

        cachedMember.reset();
    }

    public static void use(Member... membersAtAdd) {
        for (Member member : membersAtAdd) {
            CachedMember cachedMember = getCachedMember(member.getId().asString());
            if (cachedMember == null)
                members.put(member.getId().asString(), new CachedMember(member, System.currentTimeMillis()));
            else {
                try {
                    cachedMember.use(member);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static void slash(final String memberID) {
        members.remove(memberID);
    }

    private static CachedMember getCachedMember(final String memberID) {
        return members.get(memberID);
    }

    public static HashMap<String, CachedMember> cache() {
        return members;
    }

    public static int cacheSize() {
        return members.size();
    }

    public static boolean contain(final String memberID) {
        return members.containsKey(memberID);
    }

}
