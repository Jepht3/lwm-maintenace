<?php
/**
 * Page d'administration : création des connexions (QR code / code à coller)
 * et liste des appareils connectés.
 *
 * En multisite, la page se trouve dans l'administration du réseau
 * (Réglages du réseau > Remote Updater) car les mises à jour sont globales.
 */

if ( ! defined( 'ABSPATH' ) ) {
	exit;
}

class WPRU_Admin {

	const SLUG = 'remote-updater';

	public static function init() {
		add_action( is_multisite() ? 'network_admin_menu' : 'admin_menu', array( __CLASS__, 'menu' ) );
		add_action( 'admin_post_wpru_create', array( __CLASS__, 'handle_create' ) );
		add_action( 'admin_post_wpru_revoke', array( __CLASS__, 'handle_revoke' ) );
		add_filter( ( is_multisite() ? 'network_admin_' : '' ) . 'plugin_action_links_' . plugin_basename( WPRU_FILE ), array( __CLASS__, 'action_links' ) );
	}

	private static function capability() {
		return is_multisite() ? 'manage_network_options' : 'manage_options';
	}

	private static function page_url( $args = array() ) {
		$base = is_multisite() ? network_admin_url( 'settings.php' ) : admin_url( 'options-general.php' );
		return add_query_arg( array_merge( array( 'page' => self::SLUG ), $args ), $base );
	}

	public static function menu() {
		$hook = add_submenu_page(
			is_multisite() ? 'settings.php' : 'options-general.php',
			'Remote Updater',
			'Remote Updater',
			self::capability(),
			self::SLUG,
			array( __CLASS__, 'render' )
		);
		add_action( 'admin_print_scripts-' . $hook, array( __CLASS__, 'assets' ) );
	}

	public static function assets() {
		wp_enqueue_script( 'wpru-qrcode', WPRU_URL . 'assets/qrcode.min.js', array(), '1.4.4', true );
	}

	public static function action_links( $links ) {
		array_unshift( $links, '<a href="' . esc_url( self::page_url() ) . '">Connecter l\'app</a>' );
		return $links;
	}

	public static function handle_create() {
		if ( ! current_user_can( self::capability() ) ) {
			wp_die( 'Accès refusé.' );
		}
		check_admin_referer( 'wpru_create' );

		$name = isset( $_POST['wpru_name'] ) ? sanitize_text_field( wp_unslash( $_POST['wpru_name'] ) ) : '';
		if ( '' === $name ) {
			$name = 'Mon téléphone';
		}

		$conn = WPRU_Auth::create_connection( $name, get_current_user_id() );

		// La clé n'est affichée qu'une fois : stockée 10 minutes pour l'utilisateur courant.
		set_site_transient( 'wpru_new_key_' . get_current_user_id(), $conn['key'], 10 * MINUTE_IN_SECONDS );

		wp_safe_redirect( self::page_url( array( 'created' => $conn['id'] ) ) );
		exit;
	}

	public static function handle_revoke() {
		if ( ! current_user_can( self::capability() ) ) {
			wp_die( 'Accès refusé.' );
		}
		$id = isset( $_GET['id'] ) ? sanitize_text_field( wp_unslash( $_GET['id'] ) ) : '';
		check_admin_referer( 'wpru_revoke_' . $id );

		WPRU_Auth::revoke_connection( $id );

		wp_safe_redirect( self::page_url( array( 'revoked' => 1 ) ) );
		exit;
	}

	public static function render() {
		if ( ! current_user_can( self::capability() ) ) {
			return;
		}

		$new_key = null;
		if ( isset( $_GET['created'] ) ) {
			$transient = 'wpru_new_key_' . get_current_user_id();
			$new_key   = get_site_transient( $transient );
			delete_site_transient( $transient );
		}

		$connections = WPRU_Auth::get_connections();
		$is_https    = 0 === strpos( network_home_url(), 'https://' );
		?>
		<div class="wrap">
			<h1>Remote Updater</h1>
			<p>Connectez ce site à l'application Android <strong>Remote Updater</strong> pour mettre à jour WordPress, les extensions, les thèmes et les traductions depuis votre téléphone.</p>

			<?php if ( ! $is_https ) : ?>
				<div class="notice notice-warning"><p><strong>Attention :</strong> ce site n'utilise pas HTTPS. La clé d'accès circulerait en clair sur le réseau. Activez HTTPS avant de connecter l'application.</p></div>
			<?php endif; ?>

			<?php if ( ! wp_is_file_mod_allowed( 'remote_updater' ) ) : ?>
				<div class="notice notice-error"><p><code>DISALLOW_FILE_MODS</code> est actif : l'application pourra consulter les mises à jour mais pas les installer.</p></div>
			<?php endif; ?>

			<?php if ( isset( $_GET['revoked'] ) ) : ?>
				<div class="notice notice-success is-dismissible"><p>Connexion révoquée.</p></div>
			<?php endif; ?>

			<?php if ( $new_key ) : ?>
				<?php $code = WPRU_Auth::connection_code( $new_key ); ?>
				<div class="card" style="max-width:640px">
					<h2>Nouvelle connexion créée</h2>
					<p>Dans l'application, appuyez sur <strong>+ Ajouter un site</strong> puis scannez ce QR code&nbsp;:</p>
					<div id="wpru-qr" style="margin:16px 0"></div>
					<p>Ou copiez ce code de connexion dans l'application&nbsp;:</p>
					<textarea readonly rows="4" class="large-text code" onclick="this.select()"><?php echo esc_textarea( $code ); ?></textarea>
					<p><button type="button" class="button" id="wpru-copy">Copier le code</button></p>
					<details>
						<summary>Connexion manuelle</summary>
						<p>Adresse du site&nbsp;: <code><?php echo esc_html( network_home_url( '/' ) ); ?></code><br>
						Clé d'accès&nbsp;: <code><?php echo esc_html( $new_key ); ?></code></p>
					</details>
					<p class="description"><strong>Ce code ne sera plus affiché</strong> après avoir quitté cette page. Il donne un accès administrateur aux mises à jour : ne le partagez pas.</p>
				</div>
				<script>
				document.addEventListener('DOMContentLoaded', function () {
					var code = <?php echo wp_json_encode( $code ); ?>;
					if (window.qrcode) {
						var qr = qrcode(0, 'M');
						qr.addData(code);
						qr.make();
						document.getElementById('wpru-qr').innerHTML = qr.createSvgTag({ cellSize: 5, margin: 4, scalable: false });
					}
					document.getElementById('wpru-copy').addEventListener('click', function () {
						navigator.clipboard.writeText(code).then(function () {
							document.getElementById('wpru-copy').textContent = 'Copié !';
						});
					});
				});
				</script>
			<?php elseif ( isset( $_GET['created'] ) ) : ?>
				<div class="notice notice-warning"><p>Le code de cette connexion a expiré ou a déjà été affiché. Révoquez-la et créez-en une nouvelle si nécessaire.</p></div>
			<?php endif; ?>

			<h2>Connecter un appareil</h2>
			<form method="post" action="<?php echo esc_url( admin_url( 'admin-post.php' ) ); ?>">
				<input type="hidden" name="action" value="wpru_create">
				<?php wp_nonce_field( 'wpru_create' ); ?>
				<table class="form-table" role="presentation">
					<tr>
						<th scope="row"><label for="wpru_name">Nom de l'appareil</label></th>
						<td><input name="wpru_name" id="wpru_name" type="text" class="regular-text" placeholder="Ex. : Mon Pixel 8"></td>
					</tr>
				</table>
				<?php submit_button( 'Générer un code de connexion', 'primary', 'submit', false ); ?>
			</form>

			<h2 style="margin-top:32px">Appareils connectés</h2>
			<table class="widefat striped" style="max-width:900px">
				<thead>
					<tr>
						<th>Appareil</th>
						<th>Utilisateur</th>
						<th>Créée le</th>
						<th>Dernière utilisation</th>
						<th></th>
					</tr>
				</thead>
				<tbody>
				<?php if ( empty( $connections ) ) : ?>
					<tr><td colspan="5">Aucun appareil connecté.</td></tr>
				<?php endif; ?>
				<?php foreach ( $connections as $id => $c ) : ?>
					<?php $user = get_user_by( 'id', $c['user_id'] ); ?>
					<tr>
						<td><strong><?php echo esc_html( $c['name'] ); ?></strong></td>
						<td><?php echo $user ? esc_html( $user->user_login ) : '<em>supprimé</em>'; ?></td>
						<td><?php echo esc_html( wp_date( get_option( 'date_format' ) . ' H:i', $c['created'] ) ); ?></td>
						<td>
							<?php
							echo $c['last_used']
								? esc_html( sprintf( 'il y a %s', human_time_diff( $c['last_used'] ) ) . ( $c['last_ip'] ? ' — ' . $c['last_ip'] : '' ) )
								: '<em>jamais</em>';
							?>
						</td>
						<td>
							<a class="button button-link-delete"
								href="<?php echo esc_url( wp_nonce_url( admin_url( 'admin-post.php?action=wpru_revoke&id=' . rawurlencode( $id ) ), 'wpru_revoke_' . $id ) ); ?>"
								onclick="return confirm('Révoquer l\'accès de cet appareil ?');">Révoquer</a>
						</td>
					</tr>
				<?php endforeach; ?>
				</tbody>
			</table>
		</div>
		<?php
	}
}
