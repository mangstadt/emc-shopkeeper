package emcshop.presenter;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import emcshop.model.IProfileSelectorModel;
import emcshop.view.IProfileSelectorView;

public class ProfileSelectorPresenter {
    private final IProfileSelectorView view;
    private final IProfileSelectorModel model;

    private String profile;

    public ProfileSelectorPresenter(IProfileSelectorView view, IProfileSelectorModel model) {
        this.view = view;
        this.model = model;

        view.addProfileSelectedListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onProfileSelected();
            }
        });

        view.addCancelListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        });

        view.setAvailableProfiles(model.getAvailableProfiles());
        view.display();
    }

    private void onProfileSelected() {
        String profile = view.getSelectedProfile();
        if (profile == null || profile.isEmpty()) {
            view.showValidationError("Profile name cannot be blank.");
            return;
        }

        boolean created = model.createProfile(profile);
        if (!created) {
            view.showValidationError("Profile could not be created.  Try changing the name.");
            return;
        }

        this.profile = profile;
        view.close();
    }

    private void onCancel() {
        view.close();
    }

    public String getSelectedProfile() {
        return profile;
    }
}
