<?php
/**
 * Plugin Name: wooauto_updater
 * Description: Wooauto updater for Android applications, allowing users to upload APK files and manage app versions.
 * Version: 1.0.0
 * Author: Chenhao Wu
 */

// 防止直接访问
if (!defined('ABSPATH')) {
    exit;
}

class AndroidAppUpdater {
    
    private $plugin_name = 'wooauto_updater';
    private $version = '1.0.0';
    
    public function __construct() {
        add_action('init', array($this, 'init'));
        add_action('admin_menu', array($this, 'add_admin_menu'));
        add_action('rest_api_init', array($this, 'register_api_routes'));
        add_action('admin_post_upload_apk', array($this, 'handle_apk_upload'));
        add_action('admin_post_generate_token', array($this, 'generate_new_token'));
        
        // 创建上传目录
        add_action('wp_loaded', array($this, 'create_upload_directory'));
    }
    
    public function init() {
        // 初始化插件
    }
    
    // 创建上传目录
    public function create_upload_directory() {
        $upload_dir = wp_upload_dir();
        $app_updater_dir = $upload_dir['basedir'] . '/app-updater';
        
        if (!file_exists($app_updater_dir)) {
            wp_mkdir_p($app_updater_dir);
            
            // 创建.htaccess文件保护目录
            $htaccess_content = "Options -Indexes\n";
            $htaccess_content .= "<Files *.apk>\n";
            $htaccess_content .= "    Order Allow,Deny\n";
            $htaccess_content .= "    Deny from all\n";
            $htaccess_content .= "</Files>\n";
            
            file_put_contents($app_updater_dir . '/.htaccess', $htaccess_content);
        }
    }
    
    // 添加管理菜单
    public function add_admin_menu() {
        add_options_page(
            'App更新管理',
            'App更新',
            'manage_options',
            'android-app-updater',
            array($this, 'admin_page')
        );
    }
    
    // 管理页面
    public function admin_page() {
        $current_version = get_option('app_updater_version', '1.0.0');
        $current_token = get_option('app_updater_token', '');
        $apk_file = get_option('app_updater_apk_file', '');
        
        // 如果没有token，生成一个
        if (empty($current_token)) {
            $current_token = $this->generate_token();
            update_option('app_updater_token', $current_token);
        }
        
        // 处理版本号更新
        if (isset($_POST['update_version']) && wp_verify_nonce($_POST['_wpnonce'], 'update_version')) {
            $new_version = sanitize_text_field($_POST['app_version']);
            update_option('app_updater_version', $new_version);
            $current_version = $new_version;
            echo '<div class="notice notice-success"><p>版本号已更新！</p></div>';
        }
        
        ?>
        <div class="wrap">
            <h1>Android App更新管理</h1>
            
            <div class="card" style="max-width: 600px;">
                <h2>应用信息</h2>
                
                <!-- 版本号设置 -->
                <form method="post" action="">
                    <?php wp_nonce_field('update_version'); ?>
                    <table class="form-table">
                        <tr>
                            <th scope="row">当前版本号</th>
                            <td>
                                <input type="text" name="app_version" value="<?php echo esc_attr($current_version); ?>" class="regular-text" />
                                <input type="submit" name="update_version" class="button" value="更新版本" />
                            </td>
                        </tr>
                        <tr>
                            <th scope="row">访问Token</th>
                            <td>
                                <code style="background: #f1f1f1; padding: 5px;"><?php echo esc_html($current_token); ?></code>
                                <a href="<?php echo admin_url('admin-post.php?action=generate_token&_wpnonce=' . wp_create_nonce('generate_token')); ?>" class="button" onclick="return confirm('确定要重新生成Token吗？这会让旧的Token失效。')">重新生成</a>
                            </td>
                        </tr>
                    </table>
                </form>
                
                <!-- APK文件上传 -->
                <h3>APK文件管理</h3>
                <form method="post" action="<?php echo admin_url('admin-post.php'); ?>" enctype="multipart/form-data">
                    <input type="hidden" name="action" value="upload_apk">
                    <?php wp_nonce_field('upload_apk'); ?>
                    <table class="form-table">
                        <tr>
                            <th scope="row">APK文件</th>
                            <td>
                                <input type="file" name="apk_file" accept=".apk" required />
                                <input type="submit" class="button button-primary" value="上传APK" />
                                <?php if (!empty($apk_file)): ?>
                                    <p><strong>当前文件:</strong> <?php echo basename($apk_file); ?></p>
                                <?php endif; ?>
                            </td>
                        </tr>
                    </table>
                </form>
                
                <!-- API使用说明 -->
                <h3>API使用说明</h3>
                <p><strong>检查更新:</strong></p>
                <code><?php echo home_url('/wp-json/app-updater/v1/check?token=' . $current_token); ?></code>
                
                <p><strong>下载文件:</strong></p>
                <code><?php echo home_url('/wp-json/app-updater/v1/download?token=' . $current_token); ?></code>
                
                <!-- 当前状态 -->
                <h3>当前状态</h3>
                <?php if (!empty($apk_file) && file_exists($apk_file)): ?>
                    <p style="color: green;">✅ 版本 <?php echo esc_html($current_version); ?> 已就绪</p>
                    <p>文件大小: <?php echo $this->format_bytes(filesize($apk_file)); ?></p>
                <?php else: ?>
                    <p style="color: red;">❌ 请上传APK文件</p>
                <?php endif; ?>
            </div>
        </div>
        <?php
    }
    
    // 处理APK上传
    public function handle_apk_upload() {
        if (!current_user_can('manage_options') || !wp_verify_nonce($_POST['_wpnonce'], 'upload_apk')) {
            wp_die('权限不足');
        }
        
        if (!isset($_FILES['apk_file']) || $_FILES['apk_file']['error'] !== UPLOAD_ERR_OK) {
            wp_redirect(admin_url('options-general.php?page=android-app-updater&error=upload_failed'));
            exit;
        }
        
        $file = $_FILES['apk_file'];
        
        // 检查文件扩展名
        $file_extension = strtolower(pathinfo($file['name'], PATHINFO_EXTENSION));
        if ($file_extension !== 'apk') {
            wp_redirect(admin_url('options-general.php?page=android-app-updater&error=invalid_file'));
            exit;
        }
        
        // 移动文件到上传目录
        $upload_dir = wp_upload_dir();
        $target_dir = $upload_dir['basedir'] . '/app-updater/';
        $target_file = $target_dir . 'app-latest.apk';
        
        if (move_uploaded_file($file['tmp_name'], $target_file)) {
            update_option('app_updater_apk_file', $target_file);
            wp_redirect(admin_url('options-general.php?page=android-app-updater&success=1'));
        } else {
            wp_redirect(admin_url('options-general.php?page=android-app-updater&error=move_failed'));
        }
        exit;
    }
    
    // 生成新Token
    public function generate_new_token() {
        if (!current_user_can('manage_options') || !wp_verify_nonce($_GET['_wpnonce'], 'generate_token')) {
            wp_die('权限不足');
        }
        
        $new_token = $this->generate_token();
        update_option('app_updater_token', $new_token);
        
        wp_redirect(admin_url('options-general.php?page=android-app-updater&token_generated=1'));
        exit;
    }
    
    // 生成随机Token
    private function generate_token() {
        return wp_generate_password(32, false);
    }
    
    // 注册API路由
    public function register_api_routes() {
        register_rest_route('app-updater/v1', '/check', array(
            'methods' => 'GET',
            'callback' => array($this, 'api_check_update'),
            'permission_callback' => '__return_true'
        ));
        
        register_rest_route('app-updater/v1', '/download', array(
            'methods' => 'GET',
            'callback' => array($this, 'api_download_file'),
            'permission_callback' => '__return_true'
        ));
    }
    
    // API: 检查更新
    public function api_check_update($request) {
        $token = $request->get_param('token');
        $stored_token = get_option('app_updater_token', '');
        
        if (empty($token) || $token !== $stored_token) {
            return new WP_Error('invalid_token', '无效的访问令牌', array('status' => 401));
        }
        
        $current_version = get_option('app_updater_version', '1.0.0');
        $apk_file = get_option('app_updater_apk_file', '');
        
        $response = array(
            'success' => true,
            'current_version' => $current_version,
            'has_update' => !empty($apk_file) && file_exists($apk_file),
            'download_url' => home_url('/wp-json/app-updater/v1/download?token=' . $stored_token)
        );
        
        if (!empty($apk_file) && file_exists($apk_file)) {
            $response['file_size'] = filesize($apk_file);
        }
        
        return rest_ensure_response($response);
    }
    
    // API: 下载文件
    public function api_download_file($request) {
        $token = $request->get_param('token');
        $stored_token = get_option('app_updater_token', '');
        
        if (empty($token) || $token !== $stored_token) {
            return new WP_Error('invalid_token', '无效的访问令牌', array('status' => 401));
        }
        
        $apk_file = get_option('app_updater_apk_file', '');
        
        if (empty($apk_file) || !file_exists($apk_file)) {
            return new WP_Error('file_not_found', '文件不存在', array('status' => 404));
        }
        
        // 设置下载头
        header('Content-Type: application/vnd.android.package-archive');
        header('Content-Disposition: attachment; filename="app-update.apk"');
        header('Content-Length: ' . filesize($apk_file));
        header('Cache-Control: no-cache, must-revalidate');
        
        // 输出文件
        readfile($apk_file);
        exit;
    }
    
    // 格式化文件大小
    private function format_bytes($size, $precision = 2) {
        $units = array('B', 'KB', 'MB', 'GB');
        $base = log($size, 1024);
        return round(pow(1024, $base - floor($base)), $precision) . ' ' . $units[floor($base)];
    }
}

// 初始化插件
new AndroidAppUpdater();

// 插件激活时创建必要的选项
register_activation_hook(__FILE__, function() {
    if (!get_option('app_updater_version')) {
        add_option('app_updater_version', '1.0.0');
    }
    if (!get_option('app_updater_token')) {
        add_option('app_updater_token', wp_generate_password(32, false));
    }
});

// 插件停用时清理（可选）
register_deactivation_hook(__FILE__, function() {
    // 如果需要，可以在这里清理选项
    // delete_option('app_updater_version');
    // delete_option('app_updater_token');
    // delete_option('app_updater_apk_file');
});

?>