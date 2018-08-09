package top.marchand.maven.aether;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.apache.maven.settings.building.DefaultSettingsBuilder;
import org.apache.maven.settings.building.DefaultSettingsBuilderFactory;
import org.apache.maven.settings.building.DefaultSettingsBuildingRequest;
import org.apache.maven.settings.building.SettingsBuildingException;
import org.apache.maven.settings.building.SettingsBuildingRequest;
import org.apache.maven.settings.building.SettingsBuildingResult;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.util.repository.DefaultMirrorSelector;
import org.eclipse.aether.util.repository.DefaultProxySelector;
import top.marchand.maven.aether.factories.ManualRepositorySystemFactory;
import top.marchand.maven.aether.utils.ConsoleRepositoryListener;
import top.marchand.maven.aether.utils.ConsoleTransferListener;

/**
 * Utility class.
 * This code is copied from aether-demo, mainly because there is no release of
 * this poject.
 * git@github.com:eclipse/aether-demo.git
 * @author cmarchand
 */
public class AetherBooter {
    
    /**
     * Returns a new RepositorySystem, correctly initializedÒ
     * @return The new repositorySystem
     */
    public static RepositorySystem newRepositorySystem() {
        return ManualRepositorySystemFactory.newRepositorySystem();
    }
    
    /**
     * Creates a session
     * @param system The repository system to use with the session. See {@link #newRepositorySystem() }
     * @param localRepoLocation the localtion where to create local repository, 
     *  if required. A local repository may exists at this location.
     * @return a new Session
     */
    public static DefaultRepositorySystemSession newRepositorySystemSession( RepositorySystem system, String localRepoLocation) {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
        
        LocalRepository localRepo = new LocalRepository(localRepoLocation);
        session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));
        
        session.setTransferListener(new ConsoleTransferListener());
        session.setRepositoryListener(new ConsoleRepositoryListener());
        
        return session;
    }

    /**
     * Creates and return a repository list with at least central repository in.
     * @param system The system repository to use
     * @param session The session to use.
     * @return The repository list
     */
    public static List<RemoteRepository> newRepositories( RepositorySystem system, RepositorySystemSession session ) {
        return new ArrayList<>( Arrays.asList( newCentralRepository() ) );
    }
    
    /**
     * Loads maven settings and user settings into session
     * @param session The session to add settings configuration in
     */
    public static void loadLocalMavenSettings(DefaultRepositorySystemSession session) {
        DefaultSettingsBuilder settingsBuilder = new DefaultSettingsBuilderFactory().newInstance();
        SettingsBuildingRequest request = buildSettingsRequest();
        try {
            SettingsBuildingResult actualSettings = settingsBuilder.build(request);
            DefaultMirrorSelector mirrorSelector = new DefaultMirrorSelector();
            actualSettings.getEffectiveSettings().getMirrors().forEach((mirror) -> {
                mirrorSelector.add(
                        mirror.getId(),
                        mirror.getUrl(),
                        mirror.getLayout(),
                        true, 
                        mirror.getMirrorOf(),
                        null);
            });
            session.setMirrorSelector(mirrorSelector);
            DefaultProxySelector proxySelector = new DefaultProxySelector();
            actualSettings.getEffectiveSettings().getProxies().forEach((proxy) -> {
               org.eclipse.aether.repository.Proxy proxo = 
                       new org.eclipse.aether.repository.Proxy(
                               proxy.getProtocol(), 
                               proxy.getHost(), proxy.getPort());
               // no idea on how to create a Authentication object !
               proxySelector.add(proxo, proxy.getNonProxyHosts());
            });
            session.setProxySelector(proxySelector);
        } catch(SettingsBuildingException ex) {
            System.err.println("[ERROR] While trying to read maven settings");
            ex.printStackTrace(System.err);
        }
    }

    private static RemoteRepository newCentralRepository() {
        return new RemoteRepository.Builder( "central", "default", "http://central.maven.org/maven2/" ).build();
    }

    private static SettingsBuildingRequest buildSettingsRequest() {
        // user settings.xml
        File userHome = new File(System.getProperty("user.home"));
        File userSettings = new File(new File(userHome, ".m2"), "settings.xml");
        File mvnSettings = null;
        
        // try to locate maven installation dir
        String env = System.getenv("M2_HOME");
        if(env==null) {
            env = System.getenv("MVN_HOME");
        }
        if(env==null) {
            // try path
            String path = System.getenv("PATH");
            if(path!=null) {
                for(String p:path.split(File.pathSeparator)) {
                    if(p.toLowerCase().contains("maven")) {
                        File mvnBin = new File(p);
                        File m2conf = new File(mvnBin,"m2.conf");
                        if(m2conf.exists() && m2conf.isFile()) {
                            File mvnHome = mvnBin.getParentFile();
                            env=mvnHome.getAbsolutePath();
                        }
                    }
                }
            }
        }
        if(env!=null) {
            File mvnHome = new File(env);
            mvnSettings = new File(mvnHome, "settings.xml");
        }
        SettingsBuildingRequest ret = new DefaultSettingsBuildingRequest();
        if(userSettings.exists() && userSettings.isFile()) {
            ret.setUserSettingsFile(userSettings);
        }
        if(mvnSettings!=null && mvnSettings.exists() && mvnSettings.isFile()) {
            ret.setGlobalSettingsFile(mvnSettings);
        }
        return ret;
    }
    
    /**
     * Returns the location of the default repositor, in a standard maven setup.
     * @return <tt>~/.m2/repository</tt> 
     */
    public static String getLocalRepositoryPath() {
        File userHome = new File(System.getProperty("user.home"));
        File mvnDir = new File(userHome, ".m2");
        return new File(mvnDir,"repository").getAbsolutePath();
    }
}
