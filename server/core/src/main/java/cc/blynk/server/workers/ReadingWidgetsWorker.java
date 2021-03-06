package cc.blynk.server.workers;

import cc.blynk.server.core.dao.SessionDao;
import cc.blynk.server.core.dao.UserDao;
import cc.blynk.server.core.dao.UserKey;
import cc.blynk.server.core.model.DashBoard;
import cc.blynk.server.core.model.auth.Session;
import cc.blynk.server.core.model.auth.User;
import cc.blynk.server.core.model.widgets.FrequencyWidget;
import cc.blynk.server.core.model.widgets.Widget;
import cc.blynk.server.core.session.HardwareStateHolder;
import cc.blynk.utils.StateHolderUtil;
import io.netty.channel.Channel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;

/**
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 02.02.17.
 */
public class ReadingWidgetsWorker implements Runnable {

    private static final Logger log = LogManager.getLogger(ReadingWidgetsWorker.class);

    private final SessionDao sessionDao;
    private final UserDao userDao;

    public ReadingWidgetsWorker(SessionDao sessionDao, UserDao userDao) {
        this.sessionDao = sessionDao;
        this.userDao = userDao;
    }

    @Override
    public void run() {
        long now = System.currentTimeMillis();
        try {
            int tickedWidgets = process(now);
            log.debug("Ticket widgets : {}. Time : {}", tickedWidgets, System.currentTimeMillis() - now);
        } catch (Exception e) {
            log.error("Error processing reading widgets. ", e);
        }
    }

    private int process(long now) {
        int tickedWidgets = 0;
        for (Map.Entry<UserKey, Session> entry : sessionDao.userSession.entrySet()) {
            final Session session = entry.getValue();
            //for now checking widgets for active app only
            if (session.isAppConnected() && session.isHardwareConnected()) {
                final UserKey userKey = entry.getKey();
                final User user = userDao.users.get(userKey);
                for (DashBoard dashBoard : user.profile.dashBoards) {
                    if (dashBoard.isActive) {
                        for (Channel channel : session.hardwareChannels) {
                            final HardwareStateHolder stateHolder = StateHolderUtil.getHardState(channel);
                            if (stateHolder != null && stateHolder.dashId == dashBoard.id) {
                                for (Widget widget : dashBoard.widgets) {
                                    if (widget instanceof FrequencyWidget) {
                                        final FrequencyWidget frequencyWidget = (FrequencyWidget) widget;
                                        if (frequencyWidget.getDeviceId() == stateHolder.deviceId &&
                                                channel.isWritable() && frequencyWidget.isTicked(now)) {
                                            tickedWidgets++;
                                            frequencyWidget.writeReadingCommand(channel);
                                        }
                                    }
                                }
                                channel.flush();
                            }
                        }

                    }
                }
            }
        }
        return tickedWidgets;
    }

}
