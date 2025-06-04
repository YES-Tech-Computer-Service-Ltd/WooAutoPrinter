<?php
/**
 * Plugin Name: WooAutoPrinter Crash Reporter
 * Description: A comprehensive crash reporting solution for Android applications, designed to handle high volumes of reports with advanced features like file uploads, rate limiting, and detailed analytics.
 * Version: 0.0.1
 * Author: Yestech
 */

if (!defined('ABSPATH')) {
    exit;
}

class ProfessionalCrashReporter {
    
    private $table_name;
    private $upload_dir;
    private $plugin_url;
    
    public function __construct() {
        global $wpdb;
        $this->table_name = $wpdb->prefix . 'crash_reports';
        $this->upload_dir = wp_upload_dir()['basedir'] . '/crash-reports/';
        $this->plugin_url = plugin_dir_url(__FILE__);
        
        add_action('init', array($this, 'init'));
        add_action('rest_api_init', array($this, 'register_routes'));
        add_action('admin_menu', array($this, 'admin_menu'));
        add_action('admin_enqueue_scripts', array($this, 'admin_scripts'));
        add_action('wp_ajax_delete_crash_report', array($this, 'ajax_delete_report'));
        add_action('wp_ajax_bulk_delete_reports', array($this, 'ajax_bulk_delete'));
        add_action('wp_ajax_generate_api_key', array($this, 'ajax_generate_api_key'));
        add_action('crash_report_cleanup', array($this, 'cleanup_old_reports'));
        
        register_activation_hook(__FILE__, array($this, 'activate'));
        register_deactivation_hook(__FILE__, array($this, 'deactivate'));
    }
    
    public function init() {
        if (!file_exists($this->upload_dir)) {
            wp_mkdir_p($this->upload_dir);
            file_put_contents($this->upload_dir . '.htaccess', 'deny from all');
        }
    }
    
    public function activate() {
        $this->create_table();
        $this->set_default_options();
        
        if (!wp_next_scheduled('crash_report_cleanup')) {
            wp_schedule_event(time(), 'daily', 'crash_report_cleanup');
        }
    }
    
    public function deactivate() {
        wp_clear_scheduled_hook('crash_report_cleanup');
    }
    
    private function create_table() {
        global $wpdb;
        
        $charset_collate = $wpdb->get_charset_collate();
        
        $sql = "CREATE TABLE $this->table_name (
            id mediumint(9) NOT NULL AUTO_INCREMENT,
            app_version varchar(50) NOT NULL,
            android_version varchar(50) NOT NULL,
            device_model varchar(100) NOT NULL,
            device_brand varchar(50) NOT NULL,
            error_type varchar(100) NOT NULL,
            error_message text NOT NULL,
            stack_trace longtext NOT NULL,
            file_path varchar(255) DEFAULT NULL,
            user_ip varchar(45) NOT NULL,
            user_agent text DEFAULT NULL,
            app_package varchar(100) DEFAULT NULL,
            is_resolved tinyint(1) DEFAULT 0,
            severity enum('low', 'medium', 'high', 'critical') DEFAULT 'medium',
            created_at datetime DEFAULT CURRENT_TIMESTAMP,
            PRIMARY KEY (id),
            INDEX idx_created_at (created_at),
            INDEX idx_app_version (app_version),
            INDEX idx_error_type (error_type),
            INDEX idx_severity (severity),
            INDEX idx_is_resolved (is_resolved)
        ) $charset_collate;";
        
        require_once(ABSPATH . 'wp-admin/includes/upgrade.php');
        dbDelta($sql);
    }
    
    private function set_default_options() {
        if (!get_option('crash_reporter_api_key')) {
            update_option('crash_reporter_api_key', $this->generate_secure_api_key());
        }
        if (!get_option('crash_reporter_max_files')) {
            update_option('crash_reporter_max_files', 1000);
        }
        if (!get_option('crash_reporter_auto_cleanup_days')) {
            update_option('crash_reporter_auto_cleanup_days', 30);
        }
    }
    
    public function admin_menu() {
        // 主菜单
        add_menu_page(
            'Crash Reports',
            'Crash Reports',
            'manage_options',
            'crash-reports',
            array($this, 'reports_page'),
            'dashicons-warning',
            30
        );
        
        // 子菜单
        add_submenu_page(
            'crash-reports',
            'All Reports',
            'All Reports',
            'manage_options',
            'crash-reports',
            array($this, 'reports_page')
        );
        
        add_submenu_page(
            'crash-reports',
            'Settings',
            'Settings',
            'manage_options',
            'crash-reports-settings',
            array($this, 'settings_page')
        );
        
        add_submenu_page(
            'crash-reports',
            'Analytics',
            'Analytics',
            'manage_options',
            'crash-reports-analytics',
            array($this, 'analytics_page')
        );
    }
    
    public function admin_scripts($hook) {
        if (strpos($hook, 'crash-reports') !== false) {
            wp_enqueue_script('jquery');
            wp_enqueue_script('jquery-ui-datepicker');
            wp_enqueue_style('jquery-ui-css', 'https://code.jquery.com/ui/1.12.1/themes/ui-lightness/jquery-ui.css');
            
            wp_add_inline_script('jquery', $this->get_admin_js());
            wp_add_inline_style('wp-admin', $this->get_admin_css());
        }
    }
    
    public function register_routes() {
        register_rest_route('android-crash/v2', '/report', array(
            'methods' => 'POST',
            'callback' => array($this, 'handle_crash_report'),
            'permission_callback' => '__return_true'
        ));
    }
    
    public function handle_crash_report($request) {
        // API密钥验证
        $api_key = $request->get_header('X-API-Key');
        if (!$this->verify_api_key($api_key)) {
            return new WP_Error('invalid_api_key', '无效的API密钥', array('status' => 401));
        }
        
        // 检查文件数量限制
        if (!$this->check_file_limit()) {
            return new WP_Error('file_limit_exceeded', '已达到最大文件数量限制', array('status' => 507));
        }
        
        // 频率限制检查
        $rate_limit_check = $this->check_rate_limit();
        if (is_wp_error($rate_limit_check)) {
            return $rate_limit_check;
        }
        
        $params = $request->get_params();
        $files = $request->get_file_params();
        
        // 验证必需参数
        $required_fields = ['app_version', 'android_version', 'device_model', 'error_type', 'error_message', 'stack_trace'];
        foreach ($required_fields as $field) {
            if (empty($params[$field])) {
                return new WP_Error('missing_field', "缺少必需字段: $field", array('status' => 400));
            }
        }
        
        // 处理JSON文件上传
        $file_path = null;
        if (!empty($files['crash_data'])) {
            $file_result = $this->handle_json_upload($files['crash_data']);
            if (is_wp_error($file_result)) {
                return $file_result;
            }
            $file_path = $file_result;
        }
        
        // 自动判断严重程度
        $severity = $this->determine_severity($params['error_type'], $params['error_message']);
        
        // 保存到数据库
        global $wpdb;
        $result = $wpdb->insert(
            $this->table_name,
            array(
                'app_version' => sanitize_text_field($params['app_version']),
                'android_version' => sanitize_text_field($params['android_version']),
                'device_model' => sanitize_text_field($params['device_model']),
                'device_brand' => sanitize_text_field($params['device_brand'] ?? ''),
                'error_type' => sanitize_text_field($params['error_type']),
                'error_message' => sanitize_textarea_field($params['error_message']),
                'stack_trace' => sanitize_textarea_field($params['stack_trace']),
                'file_path' => $file_path,
                'user_ip' => $this->get_client_ip(),
                'user_agent' => sanitize_text_field($_SERVER['HTTP_USER_AGENT'] ?? ''),
                'app_package' => sanitize_text_field($params['app_package'] ?? ''),
                'severity' => $severity,
                'created_at' => current_time('mysql')
            )
        );
        
        if ($result === false) {
            return new WP_Error('db_error', '数据库保存失败', array('status' => 500));
        }
        
        return new WP_REST_Response(array(
            'success' => true,
            'message' => '错误报告已接收',
            'report_id' => $wpdb->insert_id,
            'severity' => $severity
        ), 200);
    }
    
    private function verify_api_key($provided_key) {
        $stored_key = get_option('crash_reporter_api_key');
        return hash_equals($stored_key, $provided_key);
    }
    
    private function check_file_limit() {
        global $wpdb;
        $max_files = intval(get_option('crash_reporter_max_files', 1000));
        $current_count = $wpdb->get_var("SELECT COUNT(*) FROM $this->table_name");
        return $current_count < $max_files;
    }
    
    private function handle_json_upload($file) {
        // 文件大小限制 (10MB)
        $max_size = 10 * 1024 * 1024;
        if ($file['size'] > $max_size) {
            return new WP_Error('file_too_large', '文件过大，最大支持10MB', array('status' => 413));
        }
        
        // 只允许JSON文件
        $file_ext = strtolower(pathinfo($file['name'], PATHINFO_EXTENSION));
        if ($file_ext !== 'json') {
            return new WP_Error('invalid_file_type', '只支持JSON文件', array('status' => 400));
        }
        
        // 验证JSON格式
        $content = file_get_contents($file['tmp_name']);
        if (json_decode($content) === null && json_last_error() !== JSON_ERROR_NONE) {
            return new WP_Error('invalid_json', '无效的JSON格式', array('status' => 400));
        }
        
        // 生成唯一文件名
        $filename = 'crash_' . uniqid() . '_' . time() . '.json';
        $filepath = $this->upload_dir . $filename;
        
        if (!move_uploaded_file($file['tmp_name'], $filepath)) {
            return new WP_Error('upload_failed', '文件上传失败', array('status' => 500));
        }
        
        return $filename;
    }
    
    private function determine_severity($error_type, $error_message) {
        $critical_keywords = ['OutOfMemoryError', 'SecurityException', 'NullPointerException'];
        $high_keywords = ['RuntimeException', 'IllegalStateException', 'ClassCastException'];
        $medium_keywords = ['IOException', 'SQLException', 'ParseException'];
        
        $error_text = strtolower($error_type . ' ' . $error_message);
        
        foreach ($critical_keywords as $keyword) {
            if (strpos($error_text, strtolower($keyword)) !== false) {
                return 'critical';
            }
        }
        
        foreach ($high_keywords as $keyword) {
            if (strpos($error_text, strtolower($keyword)) !== false) {
                return 'high';
            }
        }
        
        foreach ($medium_keywords as $keyword) {
            if (strpos($error_text, strtolower($keyword)) !== false) {
                return 'medium';
            }
        }
        
        return 'low';
    }
    
    private function check_rate_limit() {
        global $wpdb;
        $client_ip = $this->get_client_ip();
        $time_window = 300; // 5分钟
        $max_requests = 20; // 增加到20个请求
        
        $recent_count = $wpdb->get_var($wpdb->prepare(
            "SELECT COUNT(*) FROM $this->table_name 
             WHERE user_ip = %s AND created_at > DATE_SUB(NOW(), INTERVAL %d SECOND)",
            $client_ip, $time_window
        ));
        
        if ($recent_count >= $max_requests) {
            return new WP_Error('rate_limit', '请求过于频繁', array('status' => 429));
        }
        
        return true;
    }
    
    private function get_client_ip() {
        $ip_headers = array('HTTP_CF_CONNECTING_IP', 'HTTP_X_FORWARDED_FOR', 'REMOTE_ADDR');
        
        foreach ($ip_headers as $header) {
            if (!empty($_SERVER[$header])) {
                $ip = $_SERVER[$header];
                if (strpos($ip, ',') !== false) {
                    $ip = trim(explode(',', $ip)[0]);
                }
                if (filter_var($ip, FILTER_VALIDATE_IP)) {
                    return $ip;
                }
            }
        }
        
        return '0.0.0.0';
    }
    
    private function generate_secure_api_key() {
        return 'crash_' . bin2hex(random_bytes(32)) . '_' . time();
    }
    
    public function ajax_generate_api_key() {
        if (!current_user_can('manage_options')) {
            wp_die('Unauthorized');
        }
        
        $new_key = $this->generate_secure_api_key();
        update_option('crash_reporter_api_key', $new_key);
        
        wp_send_json_success(array('api_key' => $new_key));
    }
    
    public function ajax_delete_report() {
        if (!current_user_can('manage_options')) {
            wp_die('Unauthorized');
        }
        
        $report_id = intval($_POST['report_id']);
        global $wpdb;
        
        // 获取文件路径并删除文件
        $file_path = $wpdb->get_var($wpdb->prepare(
            "SELECT file_path FROM $this->table_name WHERE id = %d", $report_id
        ));
        
        if ($file_path) {
            $full_path = $this->upload_dir . $file_path;
            if (file_exists($full_path)) {
                unlink($full_path);
            }
        }
        
        // 删除数据库记录
        $result = $wpdb->delete($this->table_name, array('id' => $report_id));
        
        if ($result) {
            wp_send_json_success();
        } else {
            wp_send_json_error('删除失败');
        }
    }
    
    public function ajax_bulk_delete() {
        if (!current_user_can('manage_options')) {
            wp_die('Unauthorized');
        }
        
        $report_ids = array_map('intval', $_POST['report_ids']);
        global $wpdb;
        
        $deleted_count = 0;
        foreach ($report_ids as $report_id) {
            // 获取文件路径并删除文件
            $file_path = $wpdb->get_var($wpdb->prepare(
                "SELECT file_path FROM $this->table_name WHERE id = %d", $report_id
            ));
            
            if ($file_path) {
                $full_path = $this->upload_dir . $file_path;
                if (file_exists($full_path)) {
                    unlink($full_path);
                }
            }
            
            // 删除数据库记录
            if ($wpdb->delete($this->table_name, array('id' => $report_id))) {
                $deleted_count++;
            }
        }
        
        wp_send_json_success(array('deleted_count' => $deleted_count));
    }
    
    // 添加自动清理功能
    public function cleanup_old_reports() {
        global $wpdb;
        $cleanup_days = get_option('crash_reporter_auto_cleanup_days', 30);
        
        // 获取需要删除的文件
        $old_reports = $wpdb->get_results($wpdb->prepare(
            "SELECT id, file_path FROM $this->table_name WHERE created_at < DATE_SUB(NOW(), INTERVAL %d DAY)",
            $cleanup_days
        ));
        
        $deleted_count = 0;
        foreach ($old_reports as $report) {
            // 删除文件
            if ($report->file_path) {
                $full_path = $this->upload_dir . $report->file_path;
                if (file_exists($full_path)) {
                    unlink($full_path);
                }
            }
            
            // 删除数据库记录
            if ($wpdb->delete($this->table_name, array('id' => $report->id))) {
                $deleted_count++;
            }
        }
        
        return $deleted_count;
    }
    
    public function reports_page() {
        echo $this->get_template('reports-page.php');
    }
    
    public function settings_page() {
        if (isset($_POST['submit'])) {
            update_option('crash_reporter_max_files', intval($_POST['max_files']));
            update_option('crash_reporter_auto_cleanup_days', intval($_POST['cleanup_days']));
            echo '<div class="notice notice-success"><p>设置已保存</p></div>';
        }
        
        echo $this->get_template('settings-page.php');
    }
    
    public function analytics_page() {
        echo $this->get_template('analytics-page.php');
    }
    
    private function get_template($template_name) {
        $templates_dir = plugin_dir_path(__FILE__) . 'templates/';
        
        // 如果模板文件不存在，生成内联模板
        switch ($template_name) {
            case 'reports-page.php':
                // 获取当前页面的变量
                global $wpdb;
                $where_conditions = array('1=1');
                $where_values = array();
                
                if (!empty($_GET['app_version'])) {
                    $where_conditions[] = 'app_version = %s';
                    $where_values[] = sanitize_text_field($_GET['app_version']);
                }
                
                if (!empty($_GET['severity'])) {
                    $where_conditions[] = 'severity = %s';
                    $where_values[] = sanitize_text_field($_GET['severity']);
                }
                
                if (!empty($_GET['error_type'])) {
                    $where_conditions[] = 'error_type LIKE %s';
                    $where_values[] = '%' . sanitize_text_field($_GET['error_type']) . '%';
                }
                
                if (!empty($_GET['date_from'])) {
                    $where_conditions[] = 'created_at >= %s';
                    $where_values[] = sanitize_text_field($_GET['date_from']) . ' 00:00:00';
                }
                
                if (!empty($_GET['date_to'])) {
                    $where_conditions[] = 'created_at <= %s';
                    $where_values[] = sanitize_text_field($_GET['date_to']) . ' 23:59:59';
                }
                
                if (isset($_GET['resolved']) && $_GET['resolved'] !== '') {
                    $where_conditions[] = 'is_resolved = %d';
                    $where_values[] = intval($_GET['resolved']);
                }
                
                $where_clause = implode(' AND ', $where_conditions);
                
                $page = isset($_GET['paged']) ? max(1, intval($_GET['paged'])) : 1;
                $per_page = 20;
                $offset = ($page - 1) * $per_page;
                
                if (!empty($where_values)) {
                    $total_query = "SELECT COUNT(*) FROM $this->table_name WHERE $where_clause";
                    $total_items = $wpdb->get_var($wpdb->prepare($total_query, $where_values));
                } else {
                    $total_items = $wpdb->get_var("SELECT COUNT(*) FROM $this->table_name WHERE $where_clause");
                }
                
                if (!empty($where_values)) {
                    $query = "SELECT * FROM $this->table_name WHERE $where_clause ORDER BY created_at DESC LIMIT %d OFFSET %d";
                    $where_values[] = $per_page;
                    $where_values[] = $offset;
                    $reports = $wpdb->get_results($wpdb->prepare($query, $where_values));
                } else {
                    $query = "SELECT * FROM $this->table_name WHERE $where_clause ORDER BY created_at DESC LIMIT %d OFFSET %d";
                    $reports = $wpdb->get_results($wpdb->prepare($query, $per_page, $offset));
                }
                
                $app_versions = $wpdb->get_col("SELECT DISTINCT app_version FROM $this->table_name ORDER BY app_version DESC");
                $error_types = $wpdb->get_col("SELECT DISTINCT error_type FROM $this->table_name ORDER BY error_type");
                
                return $this->generate_reports_template($reports, $app_versions, $error_types, $total_items, $page, $per_page);
                
            case 'settings-page.php':
                $current_api_key = get_option('crash_reporter_api_key');
                $max_files = get_option('crash_reporter_max_files', 1000);
                $cleanup_days = get_option('crash_reporter_auto_cleanup_days', 30);
                return $this->generate_settings_template($current_api_key, $max_files, $cleanup_days);
                
            case 'analytics-page.php':
                global $wpdb;
                $stats = array();
                $stats['total_reports'] = $wpdb->get_var("SELECT COUNT(*) FROM $this->table_name");
                $stats['unresolved_reports'] = $wpdb->get_var("SELECT COUNT(*) FROM $this->table_name WHERE is_resolved = 0");
                $stats['critical_reports'] = $wpdb->get_var("SELECT COUNT(*) FROM $this->table_name WHERE severity = 'critical'");
                $stats['today_reports'] = $wpdb->get_var("SELECT COUNT(*) FROM $this->table_name WHERE DATE(created_at) = CURDATE()");
                
                $version_stats = $wpdb->get_results("
                    SELECT app_version, COUNT(*) as count 
                    FROM $this->table_name 
                    GROUP BY app_version 
                    ORDER BY count DESC 
                    LIMIT 10
                ");
                
                $error_type_stats = $wpdb->get_results("
                    SELECT error_type, COUNT(*) as count 
                    FROM $this->table_name 
                    GROUP BY error_type 
                    ORDER BY count DESC 
                    LIMIT 10
                ");
                
                $trend_data = $wpdb->get_results("
                    SELECT DATE(created_at) as date, COUNT(*) as count 
                    FROM $this->table_name 
                    WHERE created_at >= DATE_SUB(NOW(), INTERVAL 7 DAY) 
                    GROUP BY DATE(created_at) 
                    ORDER BY date ASC
                ");
                
                return $this->generate_analytics_template($stats, $version_stats, $error_type_stats, $trend_data);
        }
        
        return '';
    }
    
    private function generate_reports_template($reports, $app_versions, $error_types, $total_items, $page, $per_page) {
        ob_start();
        ?>
        <div class="wrap">
            <h1>Crash Reports 
                <span class="title-count">(<?php echo number_format($total_items); ?>)</span>
            </h1>
            
            <!-- 筛选器 -->
            <div class="crash-filters">
                <form method="get" action="">
                    <input type="hidden" name="page" value="crash-reports">
                    
                    <div class="filter-row">
                        <select name="app_version">
                            <option value="">所有版本</option>
                            <?php foreach ($app_versions as $version): ?>
                                <option value="<?php echo esc_attr($version); ?>" <?php selected($_GET['app_version'] ?? '', $version); ?>>
                                    <?php echo esc_html($version); ?>
                                </option>
                            <?php endforeach; ?>
                        </select>
                        
                        <select name="severity">
                            <option value="">所有严重程度</option>
                            <option value="low" <?php selected($_GET['severity'] ?? '', 'low'); ?>>低</option>
                            <option value="medium" <?php selected($_GET['severity'] ?? '', 'medium'); ?>>中</option>
                            <option value="high" <?php selected($_GET['severity'] ?? '', 'high'); ?>>高</option>
                            <option value="critical" <?php selected($_GET['severity'] ?? '', 'critical'); ?>>严重</option>
                        </select>
                        
                        <input type="text" name="error_type" placeholder="错误类型" value="<?php echo esc_attr($_GET['error_type'] ?? ''); ?>">
                        
                        <input type="date" name="date_from" value="<?php echo esc_attr($_GET['date_from'] ?? ''); ?>">
                        <input type="date" name="date_to" value="<?php echo esc_attr($_GET['date_to'] ?? ''); ?>">
                        
                        <select name="resolved">
                            <option value="">全部状态</option>
                            <option value="0" <?php selected($_GET['resolved'] ?? '', '0'); ?>>未解决</option>
                            <option value="1" <?php selected($_GET['resolved'] ?? '', '1'); ?>>已解决</option>
                        </select>
                        
                        <input type="submit" class="button" value="筛选">
                        <a href="?page=crash-reports" class="button">重置</a>
                    </div>
                </form>
            </div>
            
            <!-- 报告列表 -->
            <table class="wp-list-table widefat fixed striped crash-reports-table">
                <thead>
                    <tr>
                        <th><input type="checkbox" id="select-all"></th>
                        <th>时间</th>
                        <th>版本</th>
                        <th>设备</th>
                        <th>错误类型</th>
                        <th>严重程度</th>
                        <th>状态</th>
                        <th>操作</th>
                    </tr>
                </thead>
                <tbody>
                    <?php foreach ($reports as $report): ?>
                    <tr data-id="<?php echo $report->id; ?>">
                        <td><input type="checkbox" class="report-checkbox" value="<?php echo $report->id; ?>"></td>
                        <td><?php echo date('Y-m-d H:i', strtotime($report->created_at)); ?></td>
                        <td><?php echo esc_html($report->app_version); ?></td>
                        <td><?php echo esc_html($report->device_model); ?></td>
                        <td><?php echo esc_html($report->error_type); ?></td>
                        <td>
                            <span class="severity-badge severity-<?php echo $report->severity; ?>">
                                <?php 
                                $severity_labels = ['low' => '低', 'medium' => '中', 'high' => '高', 'critical' => '严重'];
                                echo $severity_labels[$report->severity] ?? $report->severity;
                                ?>
                            </span>
                        </td>
                        <td>
                            <span class="status-badge <?php echo $report->is_resolved ? 'resolved' : 'unresolved'; ?>">
                                <?php echo $report->is_resolved ? '已解决' : '未解决'; ?>
                            </span>
                        </td>
                        <td>
                            <button class="button-small view-details" data-id="<?php echo $report->id; ?>">查看</button>
                            <button class="button-small delete-report" data-id="<?php echo $report->id; ?>">删除</button>
                        </td>
                    </tr>
                    <?php endforeach; ?>
                </tbody>
            </table>
            
            <!-- 分页 -->
            <?php
            $total_pages = ceil($total_items / $per_page);
            if ($total_pages > 1):
            ?>
            <div class="pagination-wrapper">
                <?php
                $base_url = remove_query_arg('paged');
                for ($i = 1; $i <= $total_pages; $i++):
                    $url = add_query_arg('paged', $i, $base_url);
                    $class = ($i == $page) ? 'current' : '';
                ?>
                    <a href="<?php echo esc_url($url); ?>" class="page-number <?php echo $class; ?>"><?php echo $i; ?></a>
                <?php endfor; ?>
            </div>
            <?php endif; ?>
        </div>
        
        <!-- 详情弹窗 -->
        <div id="crash-details-modal" style="display: none;">
            <div class="modal-content">
                <span class="close">&times;</span>
                <div id="crash-details-content"></div>
            </div>
        </div>
        <?php
        return ob_get_clean();
    }
    
    private function generate_settings_template($current_api_key, $max_files, $cleanup_days) {
        ob_start();
        ?>
        <div class="wrap">
            <h1>Crash Reporter 设置</h1>
            
            <form method="post" action="">
                <table class="form-table">
                    <tr>
                        <th scope="row">API密钥</th>
                        <td>
                            <input type="text" id="api-key-display" value="<?php echo esc_attr($current_api_key); ?>" readonly class="large-text">
                            <p class="description">
                                在Android应用中使用此密钥进行身份验证。<br>
                                <button type="button" id="generate-new-key" class="button">生成新密钥</button>
                                <button type="button" id="copy-key" class="button">复制密钥</button>
                            </p>
                        </td>
                    </tr>
                    <tr>
                        <th scope="row">最大文件数量</th>
                        <td>
                            <input type="number" name="max_files" value="<?php echo esc_attr($max_files); ?>" min="100" max="10000" step="100">
                            <p class="description">达到此数量后将拒绝新的报告上传</p>
                        </td>
                    </tr>
                    <tr>
                        <th scope="row">自动清理天数</th>
                        <td>
                            <input type="number" name="cleanup_days" value="<?php echo esc_attr($cleanup_days); ?>" min="7" max="365">
                            <p class="description">超过此天数的报告将被自动删除</p>
                        </td>
                    </tr>
                </table>
                
                <?php submit_button(); ?>
            </form>
            
            <h2>API使用说明</h2>
            <div class="api-usage-info">
                <p><strong>接口地址：</strong> <code><?php echo rest_url('android-crash/v2/report'); ?></code></p>
                <p><strong>请求方法：</strong> POST</p>
                <p><strong>认证方式：</strong> 在请求头中添加 <code>X-API-Key: <?php echo esc_html($current_api_key); ?></code></p>
                
                <h3>必需参数：</h3>
                <ul>
                    <li><code>app_version</code> - 应用版本</li>
                    <li><code>android_version</code> - Android版本</li>
                    <li><code>device_model</code> - 设备型号</li>
                    <li><code>error_type</code> - 错误类型</li>
                    <li><code>error_message</code> - 错误消息</li>
                    <li><code>stack_trace</code> - 堆栈跟踪</li>
                </ul>
                
                <h3>可选参数：</h3>
                <ul>
                    <li><code>device_brand</code> - 设备品牌</li>
                    <li><code>app_package</code> - 应用包名</li>
                    <li><code>crash_data</code> - JSON文件上传（文件字段）</li>
                </ul>
            </div>
        </div>
        <?php
        return ob_get_clean();
    }
    
    private function generate_analytics_template($stats, $version_stats, $error_type_stats, $trend_data) {
        ob_start();
        ?>
        <div class="wrap">
            <h1>数据分析</h1>
            
            <div class="stats-grid">
                <div class="stat-card">
                    <h3>总报告数</h3>
                    <div class="stat-number"><?php echo number_format($stats['total_reports']); ?></div>
                </div>
                <div class="stat-card">
                    <h3>未解决</h3>
                    <div class="stat-number"><?php echo number_format($stats['unresolved_reports']); ?></div>
                </div>
                <div class="stat-card">
                    <h3>严重错误</h3>
                    <div class="stat-number critical"><?php echo number_format($stats['critical_reports']); ?></div>
                </div>
                <div class="stat-card">
                    <h3>今日新增</h3>
                    <div class="stat-number"><?php echo number_format($stats['today_reports']); ?></div>
                </div>
            </div>
            
            <div class="analytics-row">
                <div class="analytics-col">
                    <h2>版本分布</h2>
                    <table class="wp-list-table widefat">
                        <thead>
                            <tr><th>版本</th><th>报告数</th></tr>
                        </thead>
                        <tbody>
                            <?php foreach ($version_stats as $stat): ?>
                            <tr>
                                <td><?php echo esc_html($stat->app_version); ?></td>
                                <td><?php echo number_format($stat->count); ?></td>
                            </tr>
                            <?php endforeach; ?>
                        </tbody>
                    </table>
                </div>
                
                <div class="analytics-col">
                    <h2>错误类型分布</h2>
                    <table class="wp-list-table widefat">
                        <thead>
                            <tr><th>错误类型</th><th>报告数</th></tr>
                        </thead>
                        <tbody>
                            <?php foreach ($error_type_stats as $stat): ?>
                            <tr>
                                <td><?php echo esc_html($stat->error_type); ?></td>
                                <td><?php echo number_format($stat->count); ?></td>
                            </tr>
                            <?php endforeach; ?>
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
        <?php
        return ob_get_clean();
    }
    
    private function get_admin_css() {
        return "
        .crash-filters { background: #f9f9f9; padding: 15px; margin: 20px 0; border-radius: 4px; }
        .filter-row { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
        .filter-row select, .filter-row input[type='text'], .filter-row input[type='date'] { 
            min-width: 150px; padding: 5px; 
        }
        .severity-badge { 
            padding: 3px 8px; border-radius: 3px; color: white; font-size: 11px; 
        }
        .severity-low { background: #28a745; }
        .severity-medium { background: #ffc107; color: #212529; }
        .severity-high { background: #fd7e14; }
        .severity-critical { background: #dc3545; }
        .status-badge { 
            padding: 3px 8px; border-radius: 3px; font-size: 11px; 
        }
        .status-badge.resolved { background: #28a745; color: white; }
        .status-badge.unresolved { background: #6c757d; color: white; }
        .stats-grid { 
            display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); 
            gap: 20px; margin: 20px 0; 
        }
        .stat-card { 
            background: white; padding: 20px; border-radius: 8px; 
            box-shadow: 0 2px 4px rgba(0,0,0,0.1); text-align: center; 
        }
        .stat-number { font-size: 2em; font-weight: bold; color: #0073aa; }
        .stat-number.critical { color: #dc3545; }
        .analytics-row { display: flex; gap: 20px; }
        .analytics-col { flex: 1; }
        .pagination-wrapper { text-align: center; margin: 20px 0; }
        .page-number { 
            display: inline-block; padding: 8px 12px; margin: 0 2px; 
            text-decoration: none; border: 1px solid #ddd; 
        }
        .page-number.current { background: #0073aa; color: white; }
        #crash-details-modal { 
            position: fixed; z-index: 10000; left: 0; top: 0; 
            width: 100%; height: 100%; background: rgba(0,0,0,0.5); 
        }
        .modal-content { 
            background: white; margin: 5% auto; padding: 20px; 
            border-radius: 8px; width: 80%; max-width: 800px; 
            max-height: 80%; overflow-y: auto; 
        }
        .close { float: right; font-size: 28px; cursor: pointer; }
        .api-usage-info { 
            background: #f9f9f9; padding: 20px; border-radius: 4px; 
            margin-top: 20px; 
        }
        .api-usage-info code { 
            background: #e9e9e9; padding: 2px 4px; border-radius: 2px; 
        }
        ";
    }
    
    private function get_admin_js() {
        return "
        jQuery(document).ready(function($) {
            // 生成新API密钥
            $('#generate-new-key').click(function() {
                if (confirm('确定要生成新的API密钥吗？旧密钥将立即失效。')) {
                    $.post(ajaxurl, {
                        action: 'generate_api_key',
                        _ajax_nonce: '" . wp_create_nonce('generate_api_key') . "'
                    }, function(response) {
                        if (response.success) {
                            $('#api-key-display').val(response.data.api_key);
                            alert('新API密钥已生成');
                        }
                    });
                }
            });
            
            // 复制API密钥
            $('#copy-key').click(function() {
                $('#api-key-display').select();
                document.execCommand('copy');
                alert('API密钥已复制到剪贴板');
            });
            
            // 删除单个报告
            $('.delete-report').click(function() {
                if (confirm('确定要删除此报告吗？')) {
                    var reportId = $(this).data('id');
                    var row = $(this).closest('tr');
                    
                    $.post(ajaxurl, {
                        action: 'delete_crash_report',
                        report_id: reportId
                    }, function(response) {
                        if (response.success) {
                            row.fadeOut();
                        } else {
                            alert('删除失败');
                        }
                    });
                }
            });
            
            // 全选功能
            $('#select-all').change(function() {
                $('.report-checkbox').prop('checked', this.checked);
            });
            
            // 查看详情（模拟功能）
            $('.view-details').click(function() {
                var reportId = $(this).data('id');
                // 这里可以通过AJAX获取详细信息
                $('#crash-details-content').html('<h3>报告 #' + reportId + '</h3><p>详细信息加载中...</p>');
                $('#crash-details-modal').show();
            });
            
            // 关闭模态框
            $('.close').click(function() {
                $('#crash-details-modal').hide();
            });
            
            // 点击模态框外部关闭
            $('#crash-details-modal').click(function(e) {
                if (e.target === this) {
                    $(this).hide();
                }
            });
        });
        ";
    }
}

// 初始化插件
new ProfessionalCrashReporter();
?>