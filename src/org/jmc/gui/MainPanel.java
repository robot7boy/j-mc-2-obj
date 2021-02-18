/*******************************************************************************
 * Copyright (c) 2012
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 ******************************************************************************/
package org.jmc.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.MouseInputAdapter;

import org.jmc.ChunkLoaderThread;
import org.jmc.LevelDat;
import org.jmc.Options;
import org.jmc.NBT.TAG_Double;
import org.jmc.NBT.TAG_List;
import org.jmc.util.Filesystem;
import org.jmc.util.Log;
import org.jmc.util.Messages;
import javax.swing.border.EmptyBorder;

/**
 * Main Panel containing all the content of the window.
 * 
 * @author max, danijel
 * 
 */
@SuppressWarnings({ "serial", "rawtypes", "unchecked" })
public class MainPanel extends JPanel {
	/**
	 * A small thread to speed up window startup. It is used to find saves in
	 * the minecraft save directory.
	 * 
	 * @author danijel
	 * 
	 */
	private class PopulateLoadListThread extends Thread {
		public void run() {
			File minecraft_dir = Filesystem.getMinecraftDir();
			if (minecraft_dir == null)
				return;
			File save_dir = new File(minecraft_dir.getAbsolutePath() + "/saves");

			if (!save_dir.exists())
				return;

			String last_map = MainWindow.settings.getLastLoadedMap();
			boolean found_last_save = false;

			File[] saves = save_dir.listFiles();

			String p;
			for (File f : saves) {
				if (f.isDirectory()) {
					p = f.getAbsolutePath();
					cbPath.addItem(p);
					if (p.equals(last_map))
						found_last_save = true;
				}
			}

			if (found_last_save)
				cbPath.setSelectedItem(last_map);
			else
				addPathToList(last_map);

			fillDimensionList();

			try {
				ZipInputStream zis = new ZipInputStream(
						new FileInputStream(new File(Filesystem.getMinecraftDir(), "bin/minecraft.jar")));

				ZipEntry entry = null;
				while ((entry = zis.getNextEntry()) != null) {
					if (entry.getName().equals("title/splashes.txt"))
						break;
				}
				if (entry != null) {
					BufferedReader in = new BufferedReader(new InputStreamReader(zis));
					List<String> splashes = new LinkedList<String>();
					String line;
					while ((line = in.readLine()) != null)
						splashes.add(line);
					in.close();
					int r = (int) (Math.random() * (double) splashes.size());
					MainWindow.main.setTitle("jMc2Obj - " + splashes.get(r));
				}
				zis.close();
			} catch (Exception e) {
				/* don't care enough to log this */
			}
		}
	}

	// UI elements (not described separately)
	private JButton bLoad, bGoto, bExport, bSettings, bUpdate, bAbout, bConsole;
	private JCheckBox chckbxFastRender;
	private JComboBox cbPath;
	private JComboBox cbDimension;
	private JSlider sFloor, sCeil;

	public static SpinnerModel modelPos1X, modelPos1Z, modelPos2X, modelPos2Z;

	/**
	 * Main map preview panel.
	 */
	private PreviewPanel preview;

	/**
	 * Panel containing the memory state information. Also a thread constantly
	 * making memory measurements.
	 */
	private MemoryMonitor memory_monitor;

	/**
	 * Thread object used for monitoring the state of the chunk loading thread.
	 * Necessary for restarting the thread when loading a new map.
	 */
	private ChunkLoaderThread chunk_loader = null;

	private boolean slider_pressed = false;

	/**
	 * Panel constructor.
	 */
	public MainPanel() {
		setLayout(new BorderLayout());

		// Top Toolbar
		JPanel pToolbar = new JPanel();
		pToolbar.setLayout(new BoxLayout(pToolbar, BoxLayout.PAGE_AXIS));

		JPanel pPath = new JPanel();
		pPath.setBorder(BorderFactory.createEmptyBorder(2, 2, 5, 2));
		pPath.setLayout(new BoxLayout(pPath, BoxLayout.LINE_AXIS));
		cbPath = new JComboBox();
		cbPath.setEditable(true);
		bLoad = new JButton(Messages.getString("MainPanel.LOAD_BUTTON"));
		bLoad.setEnabled(false);
		// bLoad.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
		JButton bPath = new JButton("...");
		pPath.add(bPath);
		pPath.add(cbPath);
		pPath.add(bLoad);
		
		JPanel pDimension = new JPanel();
		pPath.add(pDimension);
		pDimension.setLayout(new BoxLayout(pDimension, BoxLayout.X_AXIS));
		
		JLabel lblDimension = new JLabel(Messages.getString("MainPanel.DIMENSION"));
		lblDimension.setBorder(new EmptyBorder(0, 2, 0, 5));
		pDimension.add(lblDimension);
		cbDimension = new JComboBox();
		pDimension.add(cbDimension);

		(new PopulateLoadListThread()).start();

		// I don't see a reason for a scrollpane here, but just in case it's
		// needed later:
		// JScrollPane spPath = new JScrollPane(pPath);
		// spPath.setBorder(BorderFactory.createEmptyBorder());

		// Top Buttons panel
		JPanel pButtons = new JPanel();
		pButtons.setLayout(new BoxLayout(pButtons, BoxLayout.LINE_AXIS));
		pButtons.setBorder(BorderFactory.createEmptyBorder(0, 5, 5, 5));

		bGoto = new JButton(Messages.getString("MainPanel.GOTO_BUTTON"));
		bGoto.setEnabled(false);
		bExport = new JButton(Messages.getString("MainPanel.EXPORT_BUTTON"));
		bExport.setEnabled(false);
		bSettings = new JButton(Messages.getString("MainPanel.SETTINGS_BUTTON"));
		bUpdate = new JButton(Messages.getString("MainPanel.UPDATE_BUTTON"));
		bAbout = new JButton(Messages.getString("MainPanel.ABOUT_BUTTON"));
		bAbout.setForeground(Color.red);
		bAbout.setFont(new Font(bAbout.getFont().getFamily(), Font.BOLD, bAbout.getFont().getSize()));

		pButtons.add(bGoto);
		pButtons.add(bExport);
		pButtons.add(bSettings);
		pButtons.add(bUpdate);
		pButtons.add(bAbout);

		bConsole = new JButton(Messages.getString("MainPanel.OPEN_CONSOLE"));
		pButtons.add(bConsole);
		bConsole.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				MainWindow.consoleLog.setVisible(true);
			}
		});

		// START PREVIEW CONTROLS
		JPanel pControls = new JPanel();
		pControls.setLayout(new FlowLayout(FlowLayout.CENTER, 0, 0));
		pControls.setBorder(BorderFactory.createEmptyBorder(0, 5, 5, 5));

		// Render Options Panel
		JPanel holderPreviewOptions = new JPanel();
		holderPreviewOptions.setLayout(new FlowLayout(FlowLayout.CENTER, 0, 0));
		holderPreviewOptions.setBorder(BorderFactory.createTitledBorder(holderPreviewOptions.getBorder(),
				Messages.getString("MainPanel.PREVIEW_OPTIONS"), TitledBorder.CENTER, TitledBorder.TOP));

		chckbxFastRender = new JCheckBox(Messages.getString("MainPanel.FAST_RENDER")); // TODO
																						// Localization
		chckbxFastRender.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				preview.fastrendermode = chckbxFastRender.isSelected();
				reloadPreviewLoader();
			}
		});

		final JCheckBox chckbxShowChunks = new JCheckBox(Messages.getString("MainPanel.SHOW_CHUNKS")); // TODO
																										// Localization
		chckbxShowChunks.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				preview.showchunks = chckbxShowChunks.isSelected();
				preview.redraw(chckbxFastRender.isSelected());
				preview.repaint();
			}
		});

		final JCheckBox chckbxSelectChunks = new JCheckBox(Messages.getString("MainPanel.SEL_CHUNKS")); // TODO
																										// Localization
		chckbxSelectChunks.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				preview.selectchunks = chckbxSelectChunks.isSelected();
			}
		});

		holderPreviewOptions.add(chckbxFastRender);
		holderPreviewOptions.add(chckbxShowChunks);
		holderPreviewOptions.add(chckbxSelectChunks);

		// Floor and Ceiling Panel
		JPanel holderFloorCeil = new JPanel();
		holderFloorCeil.setLayout(new FlowLayout(FlowLayout.CENTER, 0, 0));
		holderFloorCeil.setBorder(BorderFactory.createTitledBorder(holderFloorCeil.getBorder(),
				Messages.getString("MainPanel.ALT"), TitledBorder.CENTER, TitledBorder.TOP));

		// Floor Panel
		JPanel holderMinY = new JPanel();
		holderMinY.setLayout(new BoxLayout(holderMinY, BoxLayout.LINE_AXIS));
		holderMinY.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));

		JLabel lblMinY = new JLabel(Messages.getString("PreviewPanel.FLOOR"));
		final SpinnerModel minYModel = new SpinnerNumberModel(-64, -64, 320, 1);
		final JSpinner minYSpinner = new JSpinner(minYModel);
		minYSpinner.setMaximumSize(new Dimension(75, minYSpinner.getPreferredSize().height));
		minYSpinner.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				sFloor.setValue((int) minYSpinner.getModel().getValue());
			}
		});

		holderMinY.add(lblMinY);
		holderMinY.add(minYSpinner);

		// Ceiling Panel
		JPanel holderMaxY = new JPanel();
		holderMaxY.setLayout(new BoxLayout(holderMaxY, BoxLayout.LINE_AXIS));
		holderMaxY.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));

		JLabel lblMaxY = new JLabel(Messages.getString("PreviewPanel.CEILING"));
		final SpinnerModel maxYModel = new SpinnerNumberModel(320, -64, 320, 1);
		final JSpinner maxYSpinner = new JSpinner(maxYModel);
		maxYSpinner.setMaximumSize(new Dimension(75, maxYSpinner.getPreferredSize().height));
		maxYSpinner.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				sCeil.setValue((int) maxYSpinner.getModel().getValue());
			}
		});

		holderMaxY.add(lblMaxY);
		holderMaxY.add(maxYSpinner);

		holderFloorCeil.add(holderMinY);
		holderFloorCeil.add(holderMaxY);

		// Selection Pos 1 Panel
		JPanel holderPos1 = new JPanel();
		holderPos1.setLayout(new FlowLayout(FlowLayout.CENTER, 0, 0));
		holderPos1.setBorder(BorderFactory.createTitledBorder(holderPos1.getBorder(),
				Messages.getString("MainPanel.POSITION") + " 1", TitledBorder.CENTER, TitledBorder.TOP));

		JLabel lblPos1X = new JLabel("X: ");
		modelPos1X = new SpinnerNumberModel(0, Integer.MIN_VALUE, Integer.MAX_VALUE, 1);
		final JSpinner spinnerPos1X = new JSpinner(modelPos1X);
		spinnerPos1X.setPreferredSize(new Dimension(75, spinnerPos1X.getPreferredSize().height));
		spinnerPos1X.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				preview.selection_start_x = (int) spinnerPos1X.getModel().getValue();
				preview.repaint();
			}
		});

		JLabel lblPos1Z = new JLabel("    Z: ");
		modelPos1Z = new SpinnerNumberModel(0, Integer.MIN_VALUE, Integer.MAX_VALUE, 1);
		final JSpinner spinnerPos1Z = new JSpinner(modelPos1Z);
		spinnerPos1Z.setPreferredSize(new Dimension(75, spinnerPos1Z.getPreferredSize().height));
		spinnerPos1Z.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				preview.selection_start_z = (int) spinnerPos1Z.getModel().getValue();
				preview.repaint();
			}
		});

		holderPos1.add(lblPos1X);
		holderPos1.add(spinnerPos1X);
		holderPos1.add(lblPos1Z);
		holderPos1.add(spinnerPos1Z);

		// Selection Pos 2 Panel
		JPanel holderPos2 = new JPanel();
		holderPos2.setLayout(new FlowLayout(FlowLayout.CENTER, 0, 0));
		holderPos2.setBorder(BorderFactory.createTitledBorder(holderPos2.getBorder(),
				Messages.getString("MainPanel.POSITION") + " 2", TitledBorder.CENTER, TitledBorder.TOP));

		JLabel lblPos2X = new JLabel("X: ");
		modelPos2X = new SpinnerNumberModel(0, Integer.MIN_VALUE, Integer.MAX_VALUE, 1);
		final JSpinner spinnerPos2X = new JSpinner(modelPos2X);
		spinnerPos2X.setPreferredSize(new Dimension(75, spinnerPos2X.getPreferredSize().height));
		spinnerPos2X.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				preview.selection_end_x = (int) spinnerPos2X.getModel().getValue();
				preview.repaint();
			}
		});

		JLabel lblPos2Z = new JLabel("    Z: ");
		modelPos2Z = new SpinnerNumberModel(0, Integer.MIN_VALUE, Integer.MAX_VALUE, 1);
		final JSpinner spinnerPos2Z = new JSpinner(modelPos2Z);
		spinnerPos2Z.setPreferredSize(new Dimension(75, spinnerPos2Z.getPreferredSize().height));
		spinnerPos2Z.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				preview.selection_end_z = (int) spinnerPos2Z.getModel().getValue();
				preview.repaint();
			}
		});

		holderPos2.add(lblPos2X);
		holderPos2.add(spinnerPos2X);
		holderPos2.add(lblPos2Z);
		holderPos2.add(spinnerPos2Z);

		pControls.add(holderPreviewOptions);
		pControls.add(holderFloorCeil);
		pControls.add(holderPos1);
		pControls.add(holderPos2);
		// END PREVIEW CONTROLS

		// pToolbar.add(spPath);
		pToolbar.add(pPath);
		pToolbar.add(pButtons);
		pToolbar.add(pControls);

		preview = new PreviewPanel();
		preview.setBackground(new Color(110, 150, 100));

		JPanel preview_alts = new JPanel();
		preview_alts.setLayout(new BorderLayout());
		JPanel alts = new JPanel();
		alts.setLayout(new BoxLayout(alts, BoxLayout.PAGE_AXIS));

		sFloor = new JSlider();
		sFloor.setOrientation(JSlider.VERTICAL);
		sFloor.setToolTipText(Messages.getString("MainPanel.FLOOR_SLIDER"));
		sFloor.setMinimum(0);
		sFloor.setMaximum(256);// TODO: this should really be read from the
								// file, IMO
		sFloor.setValue(0);

		sCeil = new JSlider();
		sCeil.setOrientation(JSlider.VERTICAL);
		sCeil.setToolTipText(Messages.getString("MainPanel.CEILING_SLIDER"));
		sCeil.setMinimum(0);
		sCeil.setMaximum(256);
		sCeil.setValue(256);

		memory_monitor = new MemoryMonitor();

		alts.add(sCeil);
		alts.add(sFloor);

		preview_alts.add(preview);
		preview_alts.add(alts, BorderLayout.EAST);

		add(pToolbar, BorderLayout.NORTH);
		add(preview_alts);
		add(memory_monitor, BorderLayout.SOUTH);

		ChangeListener slider_listener = new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				if (e.getSource().equals(sCeil)) {
					maxYModel.setValue(sCeil.getValue());
					if (sFloor.getValue() >= sCeil.getValue()) {
						sFloor.setValue(sCeil.getValue() - 1);
						minYModel.setValue(sCeil.getValue() - 1);
					}
				} else {
					minYModel.setValue(sFloor.getValue());
					if (sCeil.getValue() <= sFloor.getValue()) {
						sCeil.setValue(sFloor.getValue() + 1);
						maxYModel.setValue(sFloor.getValue() + 1);
					}
				}
				if (Options.worldDir != null) {
					if (!slider_pressed)
						chunk_loader.setYBounds(sFloor.getValue(), sCeil.getValue());
					preview.setAltitudes(sFloor.getValue(), sCeil.getValue());
					preview.repaint();
				}
			}
		};

		MouseInputAdapter slider_adapter = new MouseInputAdapter() {
			@Override
			public void mousePressed(MouseEvent arg0) {
				slider_pressed = true;
			}

			@Override
			public void mouseReleased(MouseEvent arg0) {
				slider_pressed = false;
				if (Options.worldDir != null) {
					chunk_loader.setYBounds(sFloor.getValue(), sCeil.getValue());
				}
			}
		};

		sCeil.addChangeListener(slider_listener);
		sCeil.addMouseListener(slider_adapter);
		sFloor.addChangeListener(slider_listener);
		sFloor.addMouseListener(slider_adapter);
		preview.setAltitudes(sFloor.getValue(), sCeil.getValue());

		bPath.addActionListener(new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent arg0) {

				JFileChooser jfc = new JFileChooser(MainWindow.settings.getLastVisitedDir());
				jfc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
				if (jfc.showDialog(MainPanel.this,
						Messages.getString("MainPanel.CHOOSE_SAVE_FOLDER")) == JFileChooser.APPROVE_OPTION) {
					String path = jfc.getSelectedFile().getAbsolutePath();
					addPathToList(path);
					MainWindow.settings.setLastVisitedDir(path);
				}

			}
		});

		cbPath.addActionListener(new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				fillDimensionList();
			}
		});

		bLoad.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				Object obj = cbPath.getSelectedItem();
				if (obj != null)
					Options.worldDir = new File(obj.toString());
				if (obj == null || !Options.worldDir.exists() || !Options.worldDir.isDirectory()) {
					JOptionPane.showMessageDialog(null, Messages.getString("MainPanel.ENTER_CORRECT_DIR"));
					Options.worldDir = null;
					return;
				}
				Options.dimension = (Integer) cbDimension.getSelectedItem();
				
				Log.info("Loading " + Options.worldDir.getName() + "...");

				LevelDat levelDat = new LevelDat(Options.worldDir);

				if (!levelDat.open()) {
					JOptionPane.showMessageDialog(null, Messages.getString("MainPanel.ERR_LEVEL"));
					return;
				}

				Log.debug(levelDat.toString());

				int player_x = 0;
				int player_z = 0;
				TAG_List pos = levelDat.getPosition();
				if (pos != null) {
					player_x = (int) ((TAG_Double) pos.getElement(0)).value;
					player_z = (int) ((TAG_Double) pos.getElement(2)).value;
				}

				int spawn_x = levelDat.getSpawnX();
				int spawn_z = levelDat.getSpawnZ();

				preview.clearImages();
				preview.setPosition(player_x, player_z);
				preview.addMarker(player_x, player_z, Color.red);
				preview.addMarker(spawn_x, spawn_z, Color.green);

				reloadPreviewLoader();

				MainWindow.settings.setLastLoadedMap(Options.worldDir.toString());
				MainWindow.export.mapLoaded();
			}
		});

		bGoto.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {

				JTextField xpos = new JTextField();
				JTextField zpos = new JTextField();
				final JComponent[] inputs = new JComponent[] { new JLabel(Messages.getString("MainPanel.GOTO_MSG")),
						new JLabel("X"), xpos, new JLabel("Z"), zpos };

				int ret = JOptionPane.showConfirmDialog(MainPanel.this, inputs,
						Messages.getString("MainPanel.GOTO_BUTTON"), JOptionPane.OK_CANCEL_OPTION,
						JOptionPane.QUESTION_MESSAGE);

				if (ret != JOptionPane.OK_OPTION)
					return;

				int x = 0, z = 0;
				try {
					x = Integer.parseInt(xpos.getText());
					z = Integer.parseInt(zpos.getText());
				} catch (NumberFormatException ex) {
					Log.error(Messages.getString("MainPanel.NUM_ERR"), ex, true);
					return;
				}

				preview.setPosition(x, z);

			}
		});

		bExport.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent arg0) {
				updateSelectionOptions();

				Rectangle win_bounds = MainWindow.main.getBounds();
				int mx = win_bounds.x + win_bounds.width / 2;
				int my = win_bounds.y + win_bounds.height / 2;
				int xw = MainWindow.export.getWidth();
				int xh = MainWindow.export.getHeight();
				MainWindow.export.setBounds(mx - xw / 2, my - xh / 2, xw, xh);

				MainWindow.export.setVisible(true);
			}
		});

		bSettings.addActionListener(new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				Point p = MainWindow.main.getLocation();
				p.x += (MainWindow.main.getWidth() - MainWindow.settings.getWidth()) / 2;
				p.y += (MainWindow.main.getHeight() - MainWindow.settings.getHeight()) / 2;
				MainWindow.settings.setLocation(p);
				MainWindow.settings.setVisible(true);

			}
		});

		bUpdate.addActionListener(new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				Point p = MainWindow.main.getLocation();
				p.x += (MainWindow.main.getWidth() - MainWindow.settings.getWidth()) / 2;
				p.y += (MainWindow.main.getHeight() - MainWindow.settings.getHeight()) / 2;
				MainWindow.update.setLocation(p);
				MainWindow.update.setVisible(true);
			}
		});

		bAbout.addActionListener(new AbstractAction() {

			@Override
			public void actionPerformed(ActionEvent arg0) {

				About.show();
			}
		});

		(new Thread(memory_monitor, "MemoryMonitor")).start();

	}

	private void addPathToList(String path) {
		for (int i = 0; i < cbPath.getItemCount(); i++) {
			if (cbPath.getItemAt(i).equals(path)) {
				cbPath.setSelectedIndex(i);
				return;
			}
		}

		cbPath.addItem(path);
		cbPath.setSelectedItem(path);
	}

	private void fillDimensionList() {
		File save_dir = new File((String) cbPath.getSelectedItem());
		if (!save_dir.isDirectory())
			return;

		cbDimension.removeAllItems();

		cbDimension.addItem(0);
		for (File f : save_dir.listFiles()) {
			if (f.isDirectory()) {
				String dirname = f.getName();
				if (dirname.startsWith("DIM")) {
					try {
						int dim_id = Integer.parseInt(dirname.substring(3));
						cbDimension.addItem(dim_id);
					} catch (NumberFormatException ex) {
						Log.info("Error parsing dimension \"" + dirname.substring(3) + "\"! Skipping...");
					}
				}
			}
		}
	}

	public void loadingFinished() {
		bLoad.setEnabled(true);
		bExport.setEnabled(true);
		bGoto.setEnabled(true);
	}

	public void highlightUpdateButton() {
		bUpdate.setForeground(Color.green);
		bUpdate.setFont(new Font(bUpdate.getFont().getFamily(), Font.BOLD, bUpdate.getFont().getSize()));
	}

	public void updateSelectionOptions() {
		Integer dim = (Integer) cbDimension.getSelectedItem();
		if (dim == null)
			return;
		Options.dimension = dim;

		Rectangle rect = preview.getSelectionBounds();
		if (rect.width == 0 || rect.height == 0) {
			Options.minX = 0;
			Options.maxX = 0;
			Options.minZ = 0;
			Options.maxZ = 0;

			Options.minY = -1;
			Options.maxY = -1;
		} else {
			Options.minX = rect.x;
			Options.maxX = rect.x + rect.width;
			Options.minZ = rect.y;
			Options.maxZ = rect.y + rect.height;

			Options.minY = sFloor.getValue();
			Options.maxY = sCeil.getValue();
		}

	}

	void reloadPreviewLoader() {
		preview.clearChunks();
		if (chunk_loader != null && chunk_loader.isRunning())
			chunk_loader.stopRunning();

		chunk_loader = new ViewChunkLoaderThread(preview);
		chunk_loader.setYBounds(sFloor.getValue(), sCeil.getValue());
		(new Thread(chunk_loader, "ViewChunkLoader")).start();
	}

}
