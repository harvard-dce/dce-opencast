/**
 * Licensed to The Apereo Foundation under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 *
 * The Apereo Foundation licenses this file to you under the Educational
 * Community License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License
 * at:
 *
 *   http://opensource.org/licenses/ecl2.txt
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 */
var ocUsers = ocUsers || {};
ocUsers.users = [];

ocUsers.init = function () {
  $.ajax( {
    url: '/users/users.json?limit=1000',
    type: 'GET',
    success: ocUsers.buildUsersView
  })
        
}

ocUsers.buildUsersView = function(data) {
  ocUsers.users = data.users.user;
  $.each(ocUsers.users, function (key, user) {
	var roles = [];
	$.each(user.roles.role, function (key, role) {
	  roles.push(role.name);
	});
    user.roles = roles.sort().join(', ');
  });
  $('#addHeader').jqotesubtpl('templates/users.tpl', {users: ocUsers.users});
  // Attach actions to the update buttons
  $(".roleButton").each(function (i) {
    $(this).click(function() {
      // POST the role array to /users/[username].json
      var row = $(this).parent().parent();
      var username = this.id.substring(7); // "button_" = 7 characters
      var url = "/users/" + username + ".json";
      var roleArray = $("#text_" + username).val().split(",");
      var roles = "[";
      for(i =0; i < roleArray.length; i++) {
        roles +="\"";
        roles += $.trim(roleArray[i]);
        roles +="\"";
        if(i < roleArray.length -1) {
          roles += ",";
        }
      }
      roles +="]";
      $.ajax({
        url: url,
        type: 'PUT',
        dataType: 'text',
        data: {
          "roles": roles
        },
        success: function() {
          row.fadeOut('slow', function() {
            row.fadeIn();
          });
        }
      });
    });
  });
}