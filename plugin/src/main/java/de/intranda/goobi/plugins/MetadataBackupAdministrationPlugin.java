package de.intranda.goobi.plugins;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import org.goobi.beans.Process;
import org.goobi.managedbeans.ProcessBean;
import org.goobi.production.enums.PluginType;
import org.goobi.production.flow.statistics.hibernate.FilterHelper;
import org.goobi.production.plugin.interfaces.IAdministrationPlugin;
import org.goobi.production.plugin.interfaces.IPushPlugin;
import org.omnifaces.cdi.PushContext;

import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.persistence.managers.ProcessManager;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import net.xeoh.plugins.base.annotations.PluginImplementation;

@PluginImplementation
@Log4j2
public class MetadataBackupAdministrationPlugin implements IAdministrationPlugin, IPushPlugin {

    private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HHmmssSSS");

    @Getter
    private String title = "intranda_administration_metadata_backup";

    @Getter @Setter
    private int limit = 10;

    @Getter
    private int resultTotal = 0;

    @Getter
    private int resultProcessed = 0;

    @Getter
    private boolean run = false;

    @Getter
    @Setter
    private String filter;

    @Getter
    private List<MetadataBackupResult> resultsLimited = new ArrayList<>();
    private List<MetadataBackupResult> results = new ArrayList<>();
    private PushContext pusher;

    /**
     * Constructor
     */
    public MetadataBackupAdministrationPlugin() {
        log.info("Metadata backup administration plugin started");
        filter = ConfigPlugins.getPluginConfig(title).getString("filter", "");
    }

    /**
     * action method to run through all processes matching the filter
     */
    public void execute() {
        run = true;

        // filter the list of all processes that should be affected
        String query = FilterHelper.criteriaBuilder(filter, false, null, null, null, true, false);
        List<Integer> tempProcesses = ProcessManager.getIdsForFilter( query);

        resultTotal = tempProcesses.size();
        resultProcessed = 0;
        results = new ArrayList<>();
        resultsLimited = new ArrayList<>();



        Runnable runnable = () -> {
            try {
                long lastPush = System.currentTimeMillis();
                for (Integer processId : tempProcesses) {
                    Process process = ProcessManager.getProcessById(processId);

                    //                  Thread.sleep(1000);
                    if (!run) {
                        break;
                    }
                    MetadataBackupResult r = new MetadataBackupResult();
                    r.setTitle(process.getTitel());
                    r.setId(process.getId());
                    // create a copy of the current meta.xml file and save it with a timestamp at the end
                    try {
                        // get a timestamp for suffix
                        Timestamp timestamp = new Timestamp(System.currentTimeMillis());

                        // meta.xml
                        Path currentMetaFile = Paths.get(process.getMetadataFilePath());
                        Path backupMetaFile = Paths.get(process.getMetadataFilePath() + "-" + sdf.format(timestamp));
                        StorageProvider.getInstance().copyFile(currentMetaFile, backupMetaFile);

                        // meta_anchor.xml
                        String anchor = process.getMetadataFilePath().replace("meta.xml", "meta_anchor.xml");
                        Path currentMetaAnchorFile = Paths.get(anchor);
                        if (StorageProvider.getInstance().isFileExists(currentMetaAnchorFile)) {
                            Path backupMetaAnchorFile = Paths.get(anchor + "-" + sdf.format(timestamp));
                            StorageProvider.getInstance().copyFile(currentMetaAnchorFile, backupMetaAnchorFile);
                        }
                    } catch (Exception e) {
                        r.setStatus("ERROR");
                        r.setMessage(e.getMessage());
                    }
                    results.add(0, r);
                    if (results.size()>limit) {
                        resultsLimited = new ArrayList<>(results.subList(0, limit));
                    } else {
                        resultsLimited = new ArrayList<>(results);
                    }
                    resultProcessed++;
                    if (pusher != null && System.currentTimeMillis() - lastPush > 1000) {
                        lastPush = System.currentTimeMillis();
                        pusher.send("update");
                    }
                }

                run = false;
                Thread.sleep(200);
                if (pusher != null) {
                    pusher.send("update");
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        };
        new Thread(runnable).start();
    }

    /**
     * show the result inside of the process list
     * 
     * @return
     */
    public String showInProcessList(String limit) {
        String search = "\"id:";
        for (MetadataBackupResult r : results) {
            if (limit.isEmpty() || limit.equals(r.getStatus())) {
                search += r.getId() + " ";
            }
        }
        search += "\"";
        ProcessBean processBean = Helper.getBeanByClass(ProcessBean.class);
        processBean.setFilter( search);
        processBean.setModusAnzeige("aktuell");
        return processBean.FilterAlleStart();
    }

    @Override
    public PluginType getType() {
        return PluginType.Administration;
    }

    @Override
    public void setPushContext(PushContext pusher) {
        this.pusher = pusher;
    }

    /**
     * get the progress in percent to render a progress bar
     * @return progress as percentage
     */
    public int getProgress() {
        return 100 * resultProcessed / resultTotal;
    }

    /**
     * stop further processing
     */
    public void cancel() {
        run = false;
    }

    @Override
    public String getGui() {
        return "/uii/plugin_administration_metadata_backup.xhtml";
    }


}
