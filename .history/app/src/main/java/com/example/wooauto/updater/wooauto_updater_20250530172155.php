<?php
/**
 * Plugin Name: wooauto_updater
 * Description: Wooauto updater for Android applications, allowing users to upload APK files and manage app versions.
 * Version: 1.0.1
 * Author: Chenhao Wu
 */

// 防止直接访问
if (!defined('ABSPATH')) {
    exit;
}

class AndroidAppUpdater {
    
    private $plugin_name = 'wooauto_updater';
    private $version = '1.0.1';
    
    public function __construct() {
        add_action('init', array($this, 'init'));
        add_action('admin_menu', array($this, 'add_admin_menu'));
        add_action('rest_api_init', array($this, 'register_api_routes'));
        add_action('admin_post_upload_apk', array($this, 'handle_apk_upload'));
        add_action('admin_post_generate_token', array($this, 'generate_new_token'));
        add_action('admin_post_delete_apk', array($this, 'handle_apk_delete'));
        
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
            
            // 设置目录权限
            chmod($app_updater_dir, 0755);
            
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
        
        // 显示消息
        if (isset($_GET['success'])) {
            echo '<div class="notice notice-success"><p>APK文件上传成功！</p></div>';
        }
        if (isset($_GET['deleted'])) {
            echo '<div class="notice notice-success"><p>APK文件已删除！</p></div>';
        }
        if (isset($_GET['token_generated'])) {
            echo '<div class="notice notice-success"><p>新的访问Token已生成！</p></div>';
        }
        if (isset($_GET['error'])) {
            $error_messages = array(
                'upload_failed' => '文件上传失败，请重试',
                'invalid_file' => '请选择有效的APK文件',
                'move_failed' => '文件移动失败，请检查目录权限',
                'delete_old_failed' => '删除旧文件失败',
                'file_too_large' => '文件太大，请选择较小的APK文件'
            );
            $error = $_GET['error'];
            $message = isset($error_messages[$error]) ? $error_messages[$error] : '发生未知错误';
            echo '<div class="notice notice-error"><p>' . esc_html($message) . '</p></div>';
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
                
                <!-- APK文件管理 -->
                <h3>APK文件管理</h3>
                
                <!-- 当前状态 -->
                <?php if (!empty($apk_file) && file_exists($apk_file)): ?>
                    <div style="background: #d4edda; border: 1px solid #c3e6cb; padding: 10px; margin-bottom: 15px; border-radius: 4px;">
                        <p style="margin: 0; color: #155724;"><strong>✅ 当前文件:</strong> <?php echo basename($apk_file); ?></p>
                        <p style="margin: 5px 0 0 0; color: #155724;">版本: <?php echo esc_html($current_version); ?> | 大小: <?php echo $this->format_bytes(filesize($apk_file)); ?></p>
                        <p style="margin: 5px 0 0 0;">
                            <a href="<?php echo admin_url('admin-post.php?action=delete_apk&_wpnonce=' . wp_create_nonce('delete_apk')); ?>" 
                               class="button button-secondary" 
                               onclick="return confirm('确定要删除当前APK文件吗？')">删除当前文件</a>
                        </p>
                    </div>
                <?php else: ?>
                    <div style="background: #f8d7da; border: 1px solid #f5c6cb; padding: 10px; margin-bottom: 15px; border-radius: 4px;">
                        <p style="margin: 0; color: #721c24;">❌ 尚未上传APK文件</p>
                    </div>
                <?php endif; ?>
                
                <form method="post" action="<?php echo admin_url('admin-post.php'); ?>" enctype="multipart/form-data">
                    <input type="hidden" name="action" value="upload_apk">
                    <?php wp_nonce_field('upload_apk'); ?>
                    <table class="form-table">
                        <tr>
                            <th scope="row"><?php echo !empty($apk_file) ? '替换APK文件' : '上传APK文件'; ?></th>
                            <td>
                                <input type="file" name="apk_file" accept=".apk" required />
                                <input type="submit" class="button button-primary" value="<?php echo !empty($apk_file) ? '替换文件' : '上传APK'; ?>" />
                                <p class="description">最大文件大小: <?php echo $this->format_bytes($this->get_max_upload_size()); ?></p>
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
                
                <!-- 测试API -->
                <h3>API测试</h3>
                <p>
                    <a href="<?php echo home_url('/wp-json/app-updater/v1/check?token=' . $current_token); ?>" 
                       target="_blank" class="button">测试检查更新API</a>
                </p>
            </div>
        </div>
        <?php
    }
    
    // 处理APK上传
    public function handle_apk_upload() {
        if (!current_user_can('manage_options') || !wp_verify_nonce($_POST['_wpnonce'], 'upload_apk')) {
            wp_die('权限不足');
        }
        
        // 检查文件上传
        if (!isset($_FILES['apk_file']) || $_FILES['apk_file']['error'] !== UPLOAD_ERR_OK) {
            $error_code = isset($_FILES['apk_file']['error']) ? $_FILES['apk_file']['error'] : 'unknown';
            error_log("APK Upload Error: " . $error_code);
            wp_redirect(admin_url('options-general.php?page=android-app-updater&error=upload_failed'));
            exit;
        }
        
        $file = $_FILES['apk_file'];
        
        // 检查文件大小
        if ($file['size'] > $this->get_max_upload_size()) {
            wp_redirect(admin_url('options-general.php?page=android-app-updater&error=file_too_large'));
            exit;
        }
        
        // 检查文件扩展名
        $file_extension = strtolower(pathinfo($file['name'], PATHINFO_EXTENSION));
        if ($file_extension !== 'apk') {
            wp_redirect(admin_url('options-general.php?page=android-app-updater&error=invalid_file'));
            exit;
        }
        
        // 准备目标路径
        $upload_dir = wp_upload_dir();
        $target_dir = $upload_dir['basedir'] . '/app-updater/';
        $target_file = $target_dir . 'app-latest.apk';
        $backup_file = $target_dir . 'app-backup.apk';
        
        // 如果已有文件，先备份
        if (file_exists($target_file)) {
            if (file_exists($backup_file)) {
                unlink($backup_file); // 删除旧备份
            }
            if (!rename($target_file, $backup_file)) {
                error_log("Failed to backup existing APK file");
            }
        }
        
        // 移动新文件
        if (move_uploaded_file($file['tmp_name'], $target_file)) {
            // 设置文件权限
            chmod($target_file, 0644);
            
            // 更新选项
            update_option('app_updater_apk_file', $target_file);
            
            // 删除备份文件（如果存在）
            if (file_exists($backup_file)) {
                unlink($backup_file);
            }
            
            error_log("APK uploaded successfully: " . basename($target_file));
            wp_redirect(admin_url('options-general.php?page=android-app-updater&success=1'));
        } else {
            // 如果移动失败，恢复备份
            if (file_exists($backup_file)) {
                rename($backup_file, $target_file);
            }
            error_log("Failed to move uploaded APK file");
            wp_redirect(admin_url('options-general.php?page=android-app-updater&error=move_failed'));
        }
        exit;
    }
    
    // 处理APK删除
    public function handle_apk_delete() {
        if (!current_user_can('manage_options') || !wp_verify_nonce($_GET['_wpnonce'], 'delete_apk')) {
            wp_die('权限不足');
        }
        
        $apk_file = get_option('app_updater_apk_file', '');
        
        if (!empty($apk_file) && file_exists($apk_file)) {
            if (unlink($apk_file)) {
                delete_option('app_updater_apk_file');
                wp_redirect(admin_url('options-general.php?page=android-app-updater&deleted=1'));
            } else {
                wp_redirect(admin_url('options-general.php?page=android-app-updater&error=delete_failed'));
            }
        } else {
            wp_redirect(admin_url('options-general.php?page=android-app-updater&error=file_not_found'));
        }
        exit;
    }
    
    // 获取最大上传文件大小
    private function get_max_upload_size() {
        $max_upload = wp_max_upload_size();
        $max_post = ini_get('post_max_size');
        $max_execution = ini_get('max_execution_time');
        
        if ($max_post) {
            $max_post = wp_convert_hr_to_bytes($max_post);
            $max_upload = min($max_upload, $max_post);
        }
        
        return $max_upload;
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