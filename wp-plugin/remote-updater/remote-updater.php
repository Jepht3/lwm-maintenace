<?php
/**
 * Plugin Name:       Remote Updater
 * Description:       Connectez ce site à l'application Android « Remote Updater » pour gérer les mises à jour du cœur WordPress, des extensions, des thèmes et des traductions à distance.
 * Version:           1.0.0
 * Requires at least: 5.8
 * Requires PHP:      7.4
 * Author:            Jephte
 * License:           GPL-2.0-or-later
 * Text Domain:       remote-updater
 * Network:           true
 */

if ( ! defined( 'ABSPATH' ) ) {
	exit;
}

define( 'WPRU_VERSION', '1.0.0' );
define( 'WPRU_FILE', __FILE__ );
define( 'WPRU_DIR', plugin_dir_path( __FILE__ ) );
define( 'WPRU_URL', plugin_dir_url( __FILE__ ) );

require_once WPRU_DIR . 'includes/class-wpru-auth.php';
require_once WPRU_DIR . 'includes/class-wpru-updates.php';
require_once WPRU_DIR . 'includes/class-wpru-api.php';
require_once WPRU_DIR . 'includes/class-wpru-admin.php';

add_action( 'rest_api_init', array( 'WPRU_Api', 'register_routes' ) );

if ( is_admin() ) {
	WPRU_Admin::init();
}

register_uninstall_hook( __FILE__, 'wpru_uninstall' );

/**
 * Supprime toutes les connexions à la désinstallation.
 */
function wpru_uninstall() {
	delete_site_option( WPRU_Auth::OPTION );
}
