/*
Copyright 2015 OpenMarket Ltd

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

'use strict';

var React = require('react');
var sdk = require('matrix-react-sdk')

module.exports = React.createClass({
    displayName: 'RightPanel',

    Phase : {
        Blank: 'Blank',
        None: 'None',
        MemberList: 'MemberList',
        FileList: 'FileList',
    },

    getInitialState: function() {
        return {
            phase : this.Phase.MemberList
        }
    },

    onMemberListButtonClick: function() {
        if (this.state.phase == this.Phase.None) {
            this.setState({ phase: this.Phase.MemberList });            
        }
        else {
            this.setState({ phase: this.Phase.None });
        }
    },

    onButtonsKeydown: function(event) {
        var KEY_ENTER = 13;
        var KEY_SPACE = 32;

        switch (event.which) {
            case KEY_ENTER:
            case KEY_SPACE: {
                event.target.click();
                event.stopPropagation();
                return false;
            }
        }
        return true;
    },

    render: function() {
        var memberListExpandedState =(this.state.phase == this.Phase.MemberList);
        var MemberList = sdk.getComponent('organisms.MemberList');
        var buttonGroup;
        var panel;
        if (this.props.roomId) {
            buttonGroup =
                    <div className="mx_RightPanel_headerButtonGroup">
                        <div className="mx_RightPanel_headerButton mx_RightPanel_filebutton">
                            <img src="img/file.png" width="32" height="32" title="Files" alt="Files"/>
                        </div>
                        <div className="mx_RightPanel_headerButton" role="button" tabIndex="0" aria-expanded={memberListExpandedState} onClick={this.onMemberListButtonClick} onKeyDown={this.onButtonsKeydown}>
                            <img src="img/members.png" width="32" height="32" title="Members" alt="Members"/>
                        </div>
                    </div>;

            if (this.state.phase == this.Phase.MemberList) {
                panel = <MemberList roomId={this.props.roomId} key={this.props.roomId} />
            }
        }

        return (
            <aside className="mx_RightPanel">
                <div className="mx_RightPanel_header">
                    { buttonGroup }
                </div>
                { panel }
            </aside>
        );
    }
});

